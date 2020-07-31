/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as generate from '../../../src/utils/generate';
import { CELL_ACTION } from '../../support/customCommands';
import { KIND, CELL_STATUS } from '../../../src/const';
import { ObjectAbstract } from '../../../src/api/apiInterface/pipelineInterface';
import { fetchPipelines } from '../../utils';
import { hashByGroupAndName } from '../../../src/utils/sha';
import { NodeRequest } from '../../../src/api/apiInterface/nodeInterface';
import { ElementParameters } from './../../support/customCommands';
import { SOURCE, SINK } from '../../../src/api/apiInterface/connectorInterface';
import { PipelineRequest } from '../../../src/api/apiInterface/pipelineInterface';

const node: NodeRequest = {
  hostname: generate.serviceName(),
  port: generate.port(),
  user: generate.userName(),
  password: generate.password(),
};

/* eslint-disable @typescript-eslint/no-unused-expressions */
describe('Elements', () => {
  const sharedTopicName = generate.serviceName({ prefix: 'topic' });

  before(() => {
    cy.deleteAllServices();
    cy.createWorkspace({ node });
    cy.createSharedTopic(sharedTopicName);

    // A stream is needed for our test to ensure the Toolbox stream list is visible
    cy.uploadStreamJar();
  });

  beforeEach(() => {
    cy.stopAndDeleteAllPipelines();
    cy.createPipeline();
  });

  context('Element actions', () => {
    it('should able to create connection between connector', () => {
      // Create a connection: ftpSource -> topic -> smbSink

      // Add elements
      const sourceName = generate.serviceName({ prefix: 'source' });
      const topicName = 'T1';
      const sinkName = generate.serviceName({ prefix: 'sink' });

      cy.addElements([
        {
          name: sourceName,
          kind: KIND.source,
          className: SOURCE.perf,
        },
        {
          name: topicName,
          kind: KIND.topic,
        },
        {
          name: sinkName,
          kind: KIND.sink,
          className: SINK.smb,
        },
      ]);

      cy.createConnections([sourceName, topicName, sinkName]);

      // Should have two links
      cy.get('#paper .joint-link').should('have.length', 2);
    });

    it('should able to start a connector', () => {
      // Add elements into Paper and create connection
      const { sourceName } = createSourceAndTopic();

      // Start the connector
      cy.getCell(sourceName).trigger('mouseover');
      cy.cellAction(sourceName, CELL_ACTION.start).click();

      // Should have the status of running
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.running);
    });

    it('should able to stop a connector', () => {
      const { sourceName } = createSourceAndTopic();

      // Start the connector
      cy.getCell(sourceName).trigger('mouseover');
      cy.cellAction(sourceName, CELL_ACTION.start).click();

      // It's Running
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.running);

      // Stop the connector
      cy.cellAction(sourceName, CELL_ACTION.stop).click();

      // It's stopped
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.stopped);
    });

    it('should able to open Property dialog', () => {
      // Create a source
      const sourceName = generate.serviceName({ prefix: 'source' });

      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.perf,
      });

      // The dialog title should not be visible
      cy.findByText(`Edit the property of ${sourceName}`).should(
        'not.be.visible',
      );

      // Open the dialog
      cy.getCell(sourceName).trigger('mouseover');
      cy.cellAction(sourceName, CELL_ACTION.config).click();

      // Should be visible by now
      cy.findByText(`Edit the property of ${sourceName}`).should('be.visible');

      // Close the dialog so it won't interfere test cleanup
      cy.findByTestId('property-dialog').within(() => {
        cy.get('.MuiDialogTitle-root button').click();
      });
    });

    it('should able to delete an element', () => {
      const sourceName = generate.serviceName({ prefix: 'source' });
      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.jdbc,
      });

      // Should exist after adding
      cy.get('#paper').findByText(sourceName).should('exist');

      // Delete it
      cy.removeElement(sourceName);

      // Should be gone after the deletion
      cy.get('#paper').findByText(sourceName).should('not.exist');
    });

    it('should prevent a topic from deleting if it links to running elements', () => {
      // Create and start the pipeline
      const { topicName, sourceName } = createSourceAndTopic();
      cy.startPipeline('pipeline1');

      // Try to remove the element
      cy.getCell(topicName).trigger('mouseover');
      cy.cellAction(topicName, CELL_ACTION.remove).click();

      cy.findByTestId('delete-dialog').within(() => {
        // We should display a message to tell users why they cannot do so
        cy.findByText(
          `Before deleting this topic, you must stop the pipeline components link to it: ${sourceName}`,
        ).should('exist');

        // As well as disabling the delete button
        cy.findByText('DELETE')
          .should('exist')
          .parent('button')
          .should('be.disabled')
          .and('have.class', 'Mui-disabled');

        cy.findByText('CANCEL').click();
      });

      // Stop the pipeline, and now we should able to delete it!
      cy.stopPipeline('pipeline1');

      cy.getCell(topicName).trigger('mouseover');
      cy.cellAction(topicName, CELL_ACTION.remove).click();

      cy.findByTestId('delete-dialog').findByText('DELETE').click();

      // Topic should be removed by now, but the source connector should remain in Paper
      cy.get('#paper').within(() => {
        cy.findByText(topicName).should('not.exist');
        cy.get('.joint-link').should('have.length', 0);
        cy.findByText(sourceName).should('exist');
      });
    });
  });

  // Testing different states of the Paper elements:
  // # added -> default state
  // # linked -> but not started yet
  // # pending -> starting
  // # started -> running okay
  // # failed -> started but with errors from backend
  context('Elements when added', () => {
    it('should render connector element UI', () => {
      // Use a source and sink connector for the test
      const sourceName = generate.serviceName({ prefix: 'source' });
      const sinkName = generate.serviceName({ prefix: 'sink' });

      cy.addElements([
        {
          name: sourceName,
          kind: KIND.source,
          className: SOURCE.perf,
        },
        {
          name: sinkName,
          kind: KIND.sink,
          className: SINK.hdfs,
        },
      ]);

      cy.get('#paper').within(() => {
        // Ensure the system under test are exist
        cy.get('.connector').should('have.length', 2);

        // Source is property rendered
        cy.findByText(sourceName).then(($source) => {
          const $container = $source.parents('.connector');
          const type = SOURCE.perf.split('.').pop() || '';

          cy.wrap($container).within(() => {
            // Connector type
            cy.wrap($container).findByText(type).should('exist');

            // Icon and status
            expect($container.find('.icon.stopped')).to.exist;
            expect($container.find('.icon svg')).to.exist;
            cy.findByText(/^Status$/i).should('exist');
            cy.findByText(CELL_STATUS.stopped).should('exist');
          });
        });

        // Sink is property rendered
        cy.findByText(sinkName).then(($sink) => {
          const $container = $sink.parents('.connector');
          const type = SINK.hdfs.split('.').pop() || '';

          cy.wrap($container).within(() => {
            // Connector type
            cy.wrap($container).findByText(type).should('exist');

            // Icon and status
            expect($container.find('.icon.stopped')).to.exist;
            expect($container.find('.icon svg')).to.exist;

            cy.findByText(/^Status$/i).should('exist');
            cy.findByText(CELL_STATUS.stopped).should('exist');
          });
        });
      });
    });

    it('should render stream element UI', () => {
      const streamName = generate.serviceName({ prefix: 'stream' });
      cy.addElement({
        name: streamName,
        kind: KIND.stream,
        className: SOURCE.perf,
      });

      cy.get('#paper').within(() => {
        cy.get('.stream').should('have.length', 1);

        // Stream is properly rendered
        cy.findByText(streamName)
          .should('exist')
          .then(($stream) => {
            const $container = $stream.parents('.stream');

            cy.wrap($container).within(() => {
              // Stream type, hardcoded, should find a way to making this more robust...
              cy.wrap($container).findByText('DumbStream').should('exist');

              // Icon and status
              expect($container.find('.icon.stopped')).to.exist;
              expect($container.find('.icon svg')).to.exist;

              cy.findByText(/^Status$/i).should('exist');
              cy.findByText(CELL_STATUS.stopped).should('exist');
            });
          });
      });
    });

    it('should render topic element UI', () => {
      const pipelineOnlyTopicName = 'T1';
      // Test both pipeline only and shared topic

      cy.addElements([
        {
          name: sharedTopicName,
          kind: KIND.topic,
        },
        {
          name: pipelineOnlyTopicName,
          kind: KIND.topic,
        },
      ]);

      cy.get('#paper').within(() => {
        cy.get('.topic').should('have.length', 2);

        // Shared topic
        cy.findByText(sharedTopicName)
          .should('exist')
          .then(($topic) => {
            const $container = $topic.parents('.topic');

            cy.wrap($container).within(() => {
              expect($container.find('.topic-status.running')).to.exist; // Status
            });
          });

        // Pipeline only topic
        cy.findByText(pipelineOnlyTopicName)
          .should('exist')
          .then(($topic) => {
            const $container = $topic.parents('.topic');

            cy.wrap($container).within(() => {
              expect($container.find('.topic-status.running')).to.exist; // Status
            });
          });
      });
    });

    it('Should name pipeline only topic correctly', () => {
      const topics = ['T1', 'T2', 'T2'];

      // TODO: refactor this to run without using `cy.addElement()` as the method
      // requires a topic name and doing too much in the background which makes our test unreal
      topics.forEach((topic: string) =>
        cy.addElement({ name: topic, kind: KIND.topic }),
      );

      cy.get('#paper').within(() => {
        cy.get('.topic').should('have.length', 3);
        topics.forEach((topic) => cy.findByText(topic).should('exist'));
      });
    });

    it('should render action buttons correctly', () => {
      // Connector, stream and shabondi are all using the same template,
      // so we can test them together
      const sourceName = generate.serviceName({ prefix: 'source' });
      const sinkName = generate.serviceName({ prefix: 'sink' });
      const pipelineOnlyTopicName = 'T1';

      cy.addElements([
        {
          name: sourceName,
          kind: KIND.source,
          className: SOURCE.smb,
        },
        {
          name: sinkName,
          kind: KIND.sink,
          className: SINK.ftp,
        },
        {
          name: pipelineOnlyTopicName,
          kind: KIND.topic,
        },
      ]);

      // Testing source
      cy.getCell(sourceName).trigger('mouseover');

      cy.get('#paper').within(() => {
        cy.findByText(sourceName)
          .should('exist')
          .then(($source) => {
            const $menu = $source.parents('.connector').find('.menu');

            // Menu should be visible when hovering
            expect($menu).to.be.visible;

            expect($menu.find('button').length).to.eq(5);

            // Available actions
            expect($menu.find('.link')).to.exist;
            expect($menu.find('.start.is-disabled')).to.exist;
            expect($menu.find('.stop.is-disabled')).to.exist;
            expect($menu.find('.config')).to.exist;
            expect($menu.find('.remove')).to.exist;

            // two actions disabled by default
            expect($menu.find('.is-disabled').length).to.eq(2);
          });
      });

      // Testing sink
      cy.getCell(sinkName).trigger('mouseover');

      cy.get('#paper').within(() => {
        cy.findByText(sinkName)
          .should('exist')
          .then(($sink) => {
            const $menu = $sink.parents('.connector').find('.menu');

            // Menu should be visible when hovering
            expect($menu).to.be.visible;

            // Available actions, notice that `link` is not available for sink connectors
            expect($menu.find('button').length).to.eq(4);

            expect($menu.find('.start.is-disabled')).to.exist;
            expect($menu.find('.stop.is-disabled')).to.exist;
            expect($menu.find('.config')).to.exist;
            expect($menu.find('.remove')).to.exist;

            // two actions disabled by default
            expect($menu.find('.is-disabled').length).to.eq(2);
          });
      });

      // Testing pipeline only topic
      cy.getCell(pipelineOnlyTopicName).trigger('mouseover');
      cy.get('#paper').within(() => {
        cy.findByText(pipelineOnlyTopicName)
          .should('exist')
          .then(($topic) => {
            const $menu = $topic.parents('.topic').find('.menu');

            // Menu should be visible when hovering
            expect($menu).to.be.visible;

            // Only two actions are available for topics
            expect($menu.find('button').length).to.eq(2);

            // Available actions
            expect($menu.find('.link')).to.exist;
            expect($menu.find('.remove')).to.exist;
          });
      });
    });
  });

  context('Elements when linked', () => {
    // We only need to test source connector
    // 1. Topic doesn't change its actions buttons when connected
    // 2. Stream, shabondi are both shared the same code as source connector
    it(`should render action buttons correctly when it's linked`, () => {
      const { sourceName } = createSourceAndTopic();

      // Testing source
      cy.getCell(sourceName).trigger('mouseover');

      cy.get('#paper').within(() => {
        cy.findByText(sourceName)
          .should('exist')
          .then(($source) => {
            const $menu = $source.parents('.connector').find('.menu');

            // Menu should be visible when hovering
            expect($menu).to.be.visible;

            expect($menu.find('button').length).to.eq(5);

            // Available actions
            expect($menu.find('.link.is-disabled')).to.exist;
            expect($menu.find('.start')).to.exist;
            expect($menu.find('.stop')).to.exist;
            expect($menu.find('.config')).to.exist;
            expect($menu.find('.remove')).to.exist;

            // only link is disabled
            expect($menu.find('.is-disabled').length).to.eq(1);
          });
      });
    });
  });

  context('Elements when pending', () => {
    it('should disable all action buttons', () => {
      cy.server();
      cy.route({
        method: 'PUT',
        url: 'api/connectors/*/start**',
        response: {},
      });

      const { sourceName } = createSourceAndTopic();
      cy.getCell(sourceName).trigger('mouseover');
      cy.cellAction(sourceName, CELL_ACTION.start).click();

      // Should have the status of pending
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.pending);

      // All action are disabled right now
      cy.get('#paper')
        .findByText(sourceName)
        .parents('.connector')
        .find('.menu')
        .find('.is-disabled')
        .should('have.length', 5);

      // Wait until connector is not in pending state, and so we can manually stop it later
      cy.getElementStatus(sourceName).should(
        'not.have.text',
        CELL_STATUS.pending,
      );

      // Manually stop the connector, this ensures it gets the "stop" state and could be properly cleanup later
      cy.getCell(sourceName).trigger('mouseover');
      cy.cellAction(sourceName, CELL_ACTION.stop).click();
    });
  });

  context('Elements when started', () => {
    it('should disable some of the actions when running', () => {
      const { sourceName } = createSourceAndTopic();
      cy.getCell(sourceName).trigger('mouseover');
      cy.cellAction(sourceName, CELL_ACTION.start).click();

      cy.get('#paper').within(() => {
        cy.findByText(sourceName).should(($source) => {
          const $container = $source.parents('.connector');
          const $menu = $container.find('.menu');

          // Should be running
          expect($container.find('.status-value').text()).to.eq(
            CELL_STATUS.running,
          );

          // Only stop action is not disabled
          expect($menu.find('.link.is-disabled')).to.exist;
          expect($menu.find('.start.is-disabled')).to.exist;
          expect($menu.find('.stop')).to.exist;
          expect($menu.find('.config.is-disabled')).to.exist;
          expect($menu.find('.remove.is-disabled')).to.exist;

          expect($menu.find('.is-disabled').length).to.eq(4);
        });
      });
    });
  });

  context('Elements when failed', () => {
    it('should disable some of the actions when failed', () => {
      const { sourceName } = createSourceAndTopic();
      cy.getCell(sourceName).trigger('mouseover');
      cy.cellAction(sourceName, CELL_ACTION.start).click();

      cy.wrap(null).then(async () => {
        // There's only one pipeline in our env
        const [pipelineData]: PipelineRequest[] = await fetchPipelines();

        cy.server();
        cy.route({
          method: 'GET',
          url: 'api/pipelines',
          response: [
            {
              ...pipelineData,
              objects: pipelineData.objects.map((object: ObjectAbstract) => {
                if (object.name === sourceName) {
                  return {
                    ...object,
                    state: 'FAILED',
                  };
                }

                return object;
              }),
            },
          ],
        }).as('getPipelines');

        cy.reload();
        cy.wait('@getPipelines');

        cy.findByText('pipeline1').should('exist');

        cy.get('#paper').within(() => {
          cy.findByText(sourceName).should(($source) => {
            const $container = $source.parents('.connector');
            const $menu = $container.find('.menu');

            // Should be running
            expect($container.find('.status-value').text()).to.eq(
              CELL_STATUS.failed,
            );

            // Only start and stop actions are not disabled
            expect($menu.find('.link.is-disabled')).to.exist;
            expect($menu.find('.start')).to.exist;
            expect($menu.find('.stop')).to.exist;
            expect($menu.find('.config.is-disabled')).to.exist;
            expect($menu.find('.remove.is-disabled')).to.exist;

            expect($menu.find('.is-disabled').length).to.eq(3);
          });
        });
      });
    });
  });
});

function createSourceAndTopic() {
  // Create a Perf source connector and a pipeline only topic
  // then link them together
  const sourceName = generate.serviceName({ prefix: 'source' });
  const topicName = 'T1';
  const elements: ElementParameters[] = [
    {
      name: sourceName,
      kind: KIND.source,
      className: SOURCE.ftp,
    },
    {
      name: topicName,
      kind: KIND.topic,
    },
  ];

  cy.addElements(elements);
  cy.createConnections([sourceName, topicName]);

  return { sourceName, topicName };
}
