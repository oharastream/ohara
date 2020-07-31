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
import { NodeRequest } from '../../../src/api/apiInterface/nodeInterface';
import { KIND, CELL_STATUS } from '../../../src/const';
import { SOURCE, SINK } from '../../../src/api/apiInterface/connectorInterface';
import { CELL_ACTION } from '../../support/customCommands';
import { ElementParameters } from './../../support/customCommands';

describe('Toolbar', () => {
  const node: NodeRequest = {
    hostname: generate.serviceName(),
    port: generate.port(),
    user: generate.userName(),
    password: generate.password(),
  };

  const { source, topic, stream, sink } = KIND;
  const controls = [source, topic, stream, sink];

  before(() => {
    cy.deleteAllServices();
    cy.createWorkspace({ node });

    // A stream is needed for our test to ensure the Toolbox stream list is visible
    cy.uploadStreamJar();
  });

  beforeEach(() => {
    cy.stopAndDeleteAllPipelines();
    cy.createPipeline();
  });

  context('Insert', () => {
    it('should render insert UI', () => {
      cy.get('#toolbar').within(() => {
        // Tool title
        cy.findByText('Insert').should('exist');

        // Items and their Tooltip
        controls.forEach((item) => {
          cy.findByTestId(`insert-${item}-button`)
            .should('exist')
            .findByTitle(`Open Toolbox ${item} panel`)
            .should('exist');
        });
      });
    });

    it('should able to open hidden Toolbox via insert controls', () => {
      // Hide Toolbox first
      cy.get('#toolbox')
        .should('be.visible')
        .findByTestId('close-button')
        .click();

      // It should not be visible now
      cy.get('#toolbox').should('not.be.visible');

      // Loop thur each control and use the control to open Toolbox
      controls.forEach((item) => {
        cy.get('#toolbar').findByTestId(`insert-${item}-button`).click();

        // The target panel should be opened and the others are closed
        cy.get(`#${item}-list`).should('be.visible');
        cy.get('.toolbox-list:visible').should('have.length', 1);

        // Hide the Toolbox again, in prepare of the next iteration
        cy.get('#toolbox')
          .should('be.visible')
          .findByTestId('close-button')
          .click();

        cy.get('#toolbox').should('not.be.visible');
      });
    });

    it('should toggle Toolbox panels', () => {
      // Hide the toolbox first
      cy.get('.toolbox-list:visible').should('have.length', 0);

      // Each panel should be expanded during the test
      controls.forEach((item) => {
        cy.findByTestId(`insert-${item}-button`).click();
        cy.get('#toolbox').should('be.visible');
        cy.get(`#${item}-list`).should('be.visible');
        cy.get('.toolbox-list:visible').should('have.length', 1);
      });
    });

    it('should toggle instead of opening the panel when click on an insert control more than once', () => {
      // First click will open the panel
      cy.findByTestId('insert-source-button').click();
      cy.get('#source-list').should('be.visible');
      cy.get('.toolbox-list:visible').should('have.length', 1);

      // Second click will close the panel
      cy.findByTestId('insert-source-button').click();
      cy.get('#source-list').should('not.be.visible');
      cy.get('.toolbox-list:visible').should('have.length', 0);

      // Click on another control will close the current active one and open the one it targets
      cy.findByTestId('insert-stream-button').click();
      cy.get('#stream-list').should('be.visible');
      cy.get('.toolbox-list:visible').should('have.length', 1);
    });

    it('should reset and open only one panel when more than one panel are opened', () => {
      // Open three panels
      cy.get('#toolbox').within(() => {
        cy.findByText(/^source$/i).click();
        cy.findByText(/^topic$/i).click();
        cy.findByText(/^stream$/i).click();

        cy.get('.toolbox-list:visible').should('have.length', 3);
      });

      // Click on the source panel, it should reset all panel except the source
      cy.get('#toolbar').findByTestId(`insert-${source}-button`).click();

      cy.get('#toolbox').within(() => {
        cy.get('.toolbox-list:visible').should('have.length', 1);
        cy.get('#source-list').should('be.visible');
      });
    });
  });

  context('Zoom', () => {
    it('should render zoom UI', () => {
      cy.get('#toolbar').within(() => {
        // Tool title
        cy.findByText('Zoom').should('exist');

        // Should have three buttons, zoom in, out and display which is a Mui menu
        cy.get('.zoom .MuiButtonGroup-root > button').should('have.length', 3);

        cy.findByTestId('zoom-out-button')
          .should('exist')
          .findByTitle('Zoom out')
          .should('exist');

        // Default scale
        cy.findByTestId('zoom-display').should('have.text', '100%');

        cy.findByTestId('zoom-in-button')
          .should('exist')
          .findByTitle('Zoom in')
          .should('exist');
      });
    });

    it('should able to change scale with zoom dropdown', () => {
      // The scale numbers are exactly the same of Paper connector's height,
      // and since connector width is 2.4 times of the height, so we can get the exact
      // width and height during the test by doing `scale (height) * 2.4 = width`
      // E.g., a connector in 100% scale: `100 (height) * 2.4 = 240`.
      const scales = [50, 75, 100, 150];

      // Use a Perf source connector for this test
      const sourceName = generate.serviceName({ prefix: 'source' });
      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.perf,
      });

      // Default
      cy.findByTestId('zoom-display').should('have.text', '100%');

      cy.get('#paper')
        .findByText(sourceName)
        .should('exist')
        .parents('.connector')
        .as('source');

      // Default element width and height, the width is calculate by the above-mentioned method
      cy.get('@source').then(($el) => {
        expect($el.outerWidth()).to.eq(100 * 2.4);
        expect($el.outerHeight()).to.eq(100);
      });

      // Loop thru all scales and assert the display value is updated each time
      // when the menu item is selected.
      scales.forEach((scale) => {
        changeScale(`${scale}%`);

        cy.findByTestId('zoom-display').should('have.text', `${scale}%`);

        cy.get('@source').then(($el) => {
          expect($el.outerWidth()).to.eq(scale * 2.4);
          expect($el.outerHeight()).to.eq(scale);
        });
      });
    });

    it('should disable both zoom in and out buttons when it reaches the max or min scales', () => {
      /***** Min scale *****/
      // 50% is the lowest scale we can get via zoom dropdown
      changeScale('50%');

      // So we need to click on the zoom out button again to get to `25%`
      cy.findByTestId('zoom-out-button')
        .click()
        .should('exist')
        .and('be.disabled');
      cy.findByTestId('zoom-display').should('have.text', '25%');

      /***** Max scale *****/
      changeScale('150%');
      cy.findByTestId('zoom-in-button').should('exist').and('be.disabled');
      cy.findByTestId('zoom-display').should('have.text', '150%');
    });

    it('should able to change scale with zoom in and out buttons', () => {
      // The scale numbers are exactly the same of Paper connector's height,
      // and since connector width is 2.4 times of the height, so we can get the exact
      // width and height during the test by doing `scale (height) * 2.4 = width`
      // E.g., a connector in 100% scale: `100 (height) * 2.4 = 240`.
      const scales = [25, 50, 67, 75, 80, 90, 100, 110, 125, 150];
      const maxScale = `${scales[scales.length - 1]}%`;

      const sourceName = generate.serviceName({ prefix: 'source' });
      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.perf,
      });

      // Toolbox might cover the element and cause our test to fail
      cy.get('#toolbox').findByTestId('close-button').click();

      // Reset Default to max scale
      changeScale(maxScale);
      cy.findByTestId('zoom-display').should('have.text', maxScale);

      scales.reverse().forEach((scale) => {
        cy.get('#paper')
          .findByText(sourceName)
          .should('exist')
          .parents('.connector')
          .then(($el) => {
            // Use `Math.floor` to get the exact same value when the result
            // are float numbers
            expect(Math.floor($el.outerWidth() || 0)).to.eq(
              Math.floor(scale * 2.4),
            );
            expect($el.outerHeight()).to.eq(scale);
          });

        if (scale !== 25) {
          cy.findByTestId('zoom-out-button').should('exist').click();
        }
      });
    });
  });

  context('Fit', () => {
    it('should render the fit UI', () => {
      cy.get('#toolbar').within(() => {
        // Tool title
        cy.findByText('Fit').should('exist');
        cy.findByTestId('fit-button')
          .should('exist')
          .findByTitle('Resize the paper to fit the content')
          .should('exist');
      });
    });

    it('should fit everything into the view', () => {
      const sourceName = generate.serviceName({ prefix: 'source' });
      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.perf,
      });

      // Toolbox might cover the element and cause our test to fail
      cy.get('#toolbox').findByTestId('close-button').click();

      cy.get('#paper')
        .findByText(sourceName)
        .should('exist')
        .parents('.connector')
        .as('source');

      // Default positions should not be `left` 30 and `top` 30. The positions are
      // set by our `cy.addElement` method
      cy.get('@source').then(($el) => {
        const { top, left } = $el.position();
        expect(top).not.to.eq(30);
        expect(left).not.to.eq(30);
      });

      // Fit, reset positions
      cy.findByTestId('fit-button').click();

      cy.get('@source').then(($el) => {
        const { top, left } = $el.position();
        // New positions after fit, so have exactly of:
        expect(top).to.eq(30);
        expect(left).to.eq(30);
      });
    });
  });

  context('Center', () => {
    it('should render the center UI', () => {
      cy.get('#toolbar').within(() => {
        // Tool title
        cy.findByText('Center').should('exist');

        // Disabled by default
        cy.findByTestId('center-button').should('exist').and('be.disabled');
      });
    });

    it('toggles button disable state by select and unselect a Paper element', () => {
      const sourceName = generate.serviceName({ prefix: 'source' });
      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.perf,
      });

      cy.findByTestId('center-button').as('btn');

      // The button should default to disable since no element are selected at first
      cy.get('@btn').should('exist').and('be.disabled');

      // Select the source connector and so the button should be enabled now
      cy.getCell(sourceName).click();
      cy.get('@btn').should('not.be.disabled');

      // Reset, should be disabled again
      cy.get('#paper').click();
      cy.get('@btn').should('exist').and('be.disabled');
    });

    it('should center a Paper element', () => {
      // Use a Perf source connector for this test, could be other element as well
      const sourceName = generate.serviceName({ prefix: 'source' });
      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.perf,
      });

      cy.get('#paper')
        .findByText(sourceName)
        .should('exist')
        .parents('.connector')
        .as('source');

      // Toolbox might cover the element and cause our test to fail
      cy.get('#toolbox').findByTestId('close-button').click();

      // Reset the element positions with fit, since after a fit, the element
      // should have `top` and `left` positions of 30
      cy.findByTestId('fit-button').click();

      cy.get('@source').then(($el) => {
        const { top, left } = $el.position();
        // Now the el positions are reset
        expect(top).to.eq(30);
        expect(left).to.eq(30);
      });

      // Center the element, we will have a new set of positions after this
      cy.getCell(sourceName).click();
      cy.findByTestId('center-button')
        .should('exist')
        .and('not.be.disabled')
        .click();

      // Calculate the new positions see if it's in the center of Paper
      cy.get('@source').then(($source) => {
        const { top, left } = $source.position();

        cy.get('#paper').then(($paper) => {
          const paperWidth = $paper.outerWidth() || 0;
          const paperHeight = $paper.outerHeight() || 0;
          const sourceWidth = $source.outerWidth() || 0;
          const sourceHeight = $source.outerHeight() || 0;

          // Need to account source height and width too
          const sourceTop = paperHeight / 2 - sourceHeight / 2;
          const sourceLeft = paperWidth / 2 - sourceWidth / 2;

          expect(top).to.eq(sourceTop);
          expect(left).to.eq(sourceLeft);
        });
      });
    });
  });

  context('Pipeline controls', () => {
    it('should render pipeline controls UI', () => {
      cy.get('#toolbar').within(() => {
        cy.findByText('PIPELINE').should('exist');
        cy.findByText('Actions').should('exist');

        cy.findByTestId('pipeline-controls-button').should('exist').click();
      });
      cy.findByTestId('pipeline-controls-dropdown').within(() => {
        // By default, start and stop all components are disabled if there is no Paper element exist in the Paper
        cy.findByText('Start all components')
          .should('exist')
          .and('have.class', 'Mui-disabled');

        cy.findByText('Stop all components')
          .should('exist')
          .and('have.class', 'Mui-disabled');

        cy.findByText('Delete this pipeline')
          .should('exist')
          .and('not.have.class');
      });

      // Close pipeline controls dropdown or Cypress won't be able to click on
      // other elements in later action
      cy.get('body').click();
    });

    it('should enable start and stop all components when there is a connector, stream or shabondi', () => {
      const elements: ElementParameters[] = [
        {
          name: generate.serviceName({ prefix: 'source' }),
          kind: KIND.source,
          className: SOURCE.jdbc,
        },
        {
          name: generate.serviceName({ prefix: 'sink' }),
          kind: KIND.sink,
          className: SINK.hdfs,
        },
        {
          name: 'T1',
          kind: KIND.topic,
          className: KIND.topic,
        },
        {
          name: generate.serviceName({ prefix: 'stream' }),
          kind: KIND.stream,
          className: KIND.stream,
        },
      ];

      elements.forEach(({ name, kind, className }) => {
        cy.addElement({ name, kind, className });

        cy.findByTestId('pipeline-controls-button').should('exist').click();

        if (kind === KIND.topic) {
          // Topic couldn't be stop in UI, so these two actions should still
          // be disabled even if a topic exists in the Paper
          cy.findByTestId('pipeline-controls-dropdown').within(() => {
            cy.findByText('Start all components')
              .should('exist')
              .and('have.class', 'Mui-disabled');

            cy.findByText('Stop all components')
              .should('exist')
              .and('have.class', 'Mui-disabled');
          });
        } else {
          // Should able to start / stop the pipeline when one of the element
          // are found in the Paper
          cy.findByTestId('pipeline-controls-dropdown').within(() => {
            cy.findByText('Start all components')
              .should('exist')
              .and('not.have.class', 'Mui-disabled');

            cy.findByText('Stop all components')
              .should('exist')
              .and('not.have.class', 'Mui-disabled');
          });
        }

        // Close pipeline controls dropdown or Cypress won't be able to click on
        // other elements in later action
        cy.get('body').click();

        cy.removeElement(name);
      });
    });

    it('should able to start and stop a pipeline with the start and all components button', () => {
      // Create a Perf source and than a pipeline only topic
      const sourceName = generate.serviceName({ prefix: 'source' });
      const topicName = 'T1';

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
      ]);

      // Then, link Perf source and Topic together
      cy.createConnections([sourceName, topicName]);

      // Connector should have the status of stopped after added by default
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.stopped);

      // Start the connection
      cy.findByTestId('pipeline-controls-button').should('exist').click();
      cy.findByText('Start all components').should('exist').click();

      // Ensure both connector and topic are running now
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.running);
      cy.getElementStatus(topicName, true).should(
        'have.class',
        CELL_STATUS.running,
      );

      // Stop the connection
      cy.findByTestId('pipeline-controls-button').should('exist').click();
      cy.findByText('Stop all components').should('exist').click();

      // Assert again, this time the connector should be stopped
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.stopped);

      // Topic is not affecting byt the start/stop action, and so should still be running
      cy.getElementStatus(topicName, true).should(
        'have.class',
        CELL_STATUS.running,
      );
    });

    it('should not start and stop an element without connections', () => {
      // Create a SMB source connector and add it into Paper
      const sourceName = generate.serviceName({ prefix: 'source' });

      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.smb,
      });

      cy.startPipeline('pipeline1');

      // Since it won't be triggered, the state should remain `stopped`
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.stopped);

      // Same with stop, it should still have a state of `stopped`
      cy.stopPipeline('pipeline1');
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.stopped);
    });

    // Running services -> connectors, streams, shabondies.
    it('should prevent users from deleting a pipeline if it has running services', () => {
      const sourceName = generate.serviceName({ prefix: 'source' });
      const topicName = 'T1';

      // Add a perf source connector and a pipeline-only topic
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
      ]);

      // Create connection and start pipeline
      cy.createConnections([sourceName, topicName]);
      cy.startPipeline('pipeline1');

      // The connector should be running by now
      cy.getElementStatus(sourceName).should('have.text', CELL_STATUS.running);

      // Try to delete it
      cy.get('.pipeline-controls').find('button').click();
      cy.findByText('Delete this pipeline').click();

      // A dialog should be displayed and tell users to stop the pipeline before deleting
      cy.findByTestId('delete-dialog')
        .should('exist')
        .within(() => {
          // Assert dialog, title, message and button disable state
          cy.findByText('Delete pipeline').should('exist');

          cy.findByText(
            'Oops, there are still some running services in pipeline1. You should stop them first and then you will be able to delete this pipeline.',
          );

          cy.findByText('DELETE')
            .should('exist')
            .parent('button')
            .should('be.disabled');

          cy.findByTestId('close-button').click();
        });
    });

    it('should able to delete a pipeline', () => {
      const sourceName = generate.serviceName({ prefix: 'source' });
      cy.addElement({
        name: sourceName,
        kind: KIND.source,
        className: SOURCE.perf,
      });

      // We have wrapped this feature in a custom `cy` command since it's used
      // quite often in our test code.
      cy.deletePipeline('pipeline1');
    });
  });

  context('Metrics switch', () => {
    it('should render the Metrics switch UI', () => {
      // Tool title
      cy.findByText('Metrics').should('exist');

      // The switch should not have the check class by default
      cy.findByTestId('metrics-switch')
        .should('exist')
        .and('not.have.class', 'Mui-checked');
    });

    it('toggles the switch', () => {
      // Default
      cy.findByTestId('metrics-switch')
        .should('exist')
        .and('not.have.class', 'Mui-checked')
        .as('switch');

      // On
      cy.get('@switch').click().should('have.class', 'Mui-checked');

      // Off
      cy.get('@switch').click().should('not.have.class', 'Mui-checked');
    });

    context('Paper interaction', () => {
      let sourceName = '';

      beforeEach(() => {
        // Create a Perf source and than a pipeline only topic
        sourceName = generate.serviceName({ prefix: 'source' });
        const topicName = 'T1';

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
        ]);

        // Then, link Perf source and Topic together
        cy.createConnections([sourceName, topicName]);
      });

      it('toggles paper element metrics', () => {
        cy.startPipeline('pipeline1');

        // Should not display metrics on Paper elements
        cy.get('#paper .metrics')
          .should('have.length', 1)
          .and('not.be.visible');

        // Turn on switch
        cy.findByTestId('metrics-switch')
          .should('not.have.class', 'Mui-checked')
          .click();

        // Should display metrics on Paper elements
        cy.get('#paper .metrics').should('have.length', 1).and('be.visible');

        // Reset metrics
        cy.findByTestId('metrics-switch').click();
      });

      it(`should display metrics when starts a connection with Paper element's start action button`, () => {
        // Start the source with element's start action button
        cy.getCell(sourceName).trigger('mouseover');
        cy.cellAction(sourceName, CELL_ACTION.start).click();

        // Ensure the connector is running
        cy.getElementStatus(sourceName).should(
          'have.text',
          CELL_STATUS.running,
        );

        // Enable metrics
        cy.findByTestId('metrics-switch')
          .click()
          .should('have.class', 'Mui-checked');

        // Should display metrics, although we don't have real data here
        cy.findByText('No metrics data available').should('exist');

        // Reset metrics
        cy.findByTestId('metrics-switch')
          .click()
          .should('not.have.class', 'Mui-checked');
      });

      it('should display metrics when starts a connection with start all components action', () => {
        // Start the pipeline with start all components action
        cy.startPipeline('pipeline1');

        // Ensure the connector is running
        cy.getElementStatus(sourceName).should(
          'have.text',
          CELL_STATUS.running,
        );

        // Enable metrics
        cy.findByTestId('metrics-switch')
          .click()
          .should('have.class', 'Mui-checked');

        // Should display metrics, although we don't have real data here
        cy.findByText('No metrics data available').should('exist');
      });
    });
  });
});

function changeScale(scale: string) {
  cy.findByTestId('zoom-display').click();

  cy.findByTestId('zoom-dropdown')
    .findByText(scale)
    .click()
    .should('have.text', scale);
}
