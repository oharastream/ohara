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
import { KIND } from '../../../src/const';
import { NodeRequest } from '../../../src/api/apiInterface/nodeInterface';
import { ElementParameters } from './../../support/customCommands';
import { SOURCE, SINK } from '../../../src/api/apiInterface/connectorInterface';

const node: NodeRequest = {
  hostname: generate.serviceName(),
  port: generate.port(),
  user: generate.userName(),
  password: generate.password(),
};

describe('Paper Element connections', () => {
  before(() => {
    cy.deleteAllServices();
    cy.createWorkspace({ node });
    cy.uploadStreamJar();
  });

  beforeEach(() => {
    cy.stopAndDeleteAllPipelines();
    cy.createPipeline();
  });

  context('Basic connection', () => {
    it('should verify connect logic between Paper elements', () => {
      // Prepare elements
      const perfSourceName = generate.serviceName({ prefix: 'source' });
      const ftpSourceName = generate.serviceName({ prefix: 'source' });
      const consoleSinkName = generate.serviceName({ prefix: 'sink' });
      const hdfsSinkName = generate.serviceName({ prefix: 'sink' });
      const pipelineOnlyTopicName1 = 'T1';
      const pipelineOnlyTopicName2 = 'T2';

      const elements: ElementParameters[] = [
        {
          name: perfSourceName,
          kind: KIND.source,
          className: SOURCE.perf,
        },
        {
          name: ftpSourceName,
          kind: KIND.source,
          className: SOURCE.ftp,
        },
        {
          name: pipelineOnlyTopicName1,
          kind: KIND.topic,
          className: KIND.topic,
        },
        {
          name: consoleSinkName,
          kind: KIND.sink,
          className: SINK.console,
        },
        {
          name: pipelineOnlyTopicName2,
          kind: KIND.topic,
          className: KIND.topic,
        },
        {
          name: hdfsSinkName,
          kind: KIND.sink,
          className: SINK.hdfs,
        },
      ];

      // Add elements to Paper
      cy.addElements(elements);

      // ⛔️ Cannot link two source connectors together: perfSource -> ftpSource
      cy.createConnections([perfSourceName, ftpSourceName], false);
      cy.findByText(`Target ${ftpSourceName} is a source!`)
        .should('exist')
        .siblings('div')
        .first()
        .click();

      // ⛔️ Cannot link two topics together: pipelineOnlyTopic1 -> pipelineOnlyTopic2
      cy.createConnections(
        [pipelineOnlyTopicName1, pipelineOnlyTopicName2],
        false,
      );
      cy.findByText(
        `Cannot connect a ${KIND.topic} to another ${KIND.topic}, they both have the same type`,
      )
        .should('exist')
        .siblings('div')
        .first()
        .click();

      // Create a connection between PerfSource -> pipelineOnlyTopic1 -> hdfsSink
      cy.createConnections([
        perfSourceName,
        pipelineOnlyTopicName1,
        hdfsSinkName,
      ]);

      // Should create two links
      cy.get('#paper .joint-link').should('have.length', 2);

      // ⛔️ Cannot link a source to a sink where the sink already has a connection: ftpSource -> hdfsSink
      cy.createConnections([ftpSourceName, hdfsSinkName], false);
      cy.findByText(
        `The target ${hdfsSinkName} is already connected to a source`,
      )
        .should('exist')
        .siblings('div')
        .first()
        .click();

      // ⛔️ Cannot link a topic to a sink where the sink already has a connection: pipelineOnlyTopic2 -> hdfsSink
      cy.createConnections([pipelineOnlyTopicName2, hdfsSinkName], false);
      cy.findByText(
        `The target ${hdfsSinkName} is already connected to a source`,
      )
        .should('exist')
        .siblings('div')
        .first()
        .click();
    });

    it('should able to delete a connected topic', () => {
      const perfSourceName = generate.serviceName({ prefix: 'source' });
      const pipelineOnlyTopicName = 'T1';
      const smbSinkName = generate.serviceName({ prefix: 'sink' });

      // Add elements
      cy.addElements([
        {
          name: perfSourceName,
          kind: KIND.source,
          className: SOURCE.perf,
        },
        {
          name: pipelineOnlyTopicName,
          kind: KIND.topic,
          className: KIND.topic,
        },
        {
          name: smbSinkName,
          kind: KIND.sink,
          className: SINK.smb,
        },
      ]);

      // Create connections
      cy.createConnections([
        perfSourceName,
        pipelineOnlyTopicName,
        smbSinkName,
      ]);

      cy.get('#paper .paper-element').should('have.length', 3);
      cy.get('#paper .joint-link').should('have.length', 2);

      // we can delete a connected topic and remove the links
      cy.removeElement(pipelineOnlyTopicName);

      cy.findByText(pipelineOnlyTopicName).should('not.exist');
      cy.get('#paper .paper-element').should('have.length', 2);
      cy.get('#paper .joint-link').should('have.length', 0);
    });

    it('should create a full connection', () => {
      const perfSourceName = generate.serviceName({ prefix: 'source' });
      const ftpSourceName = generate.serviceName({ prefix: 'source' });
      const consoleSinkName = generate.serviceName({ prefix: 'sink' });
      const hdfsSinkName = generate.serviceName({ prefix: 'sink' });
      const pipelineOnlyTopicName1 = 'T1';
      const streamName = generate.serviceName({ prefix: 'stream' });
      const pipelineOnlyTopicName2 = 'T2';

      cy.addElements([
        {
          name: perfSourceName,
          kind: KIND.source,
          className: SOURCE.perf,
        },
        {
          name: ftpSourceName,
          kind: KIND.source,
          className: SOURCE.ftp,
        },
        {
          name: consoleSinkName,
          kind: KIND.sink,
          className: SINK.console,
        },
        {
          name: hdfsSinkName,
          kind: KIND.sink,
          className: SINK.hdfs,
        },
        {
          name: pipelineOnlyTopicName1,
          kind: KIND.topic,
        },
        {
          name: streamName,
          kind: KIND.stream,
        },
        {
          name: pipelineOnlyTopicName2,
          kind: KIND.topic,
        },
      ]);

      //  perf source -> topic1 -> stream -> topic2 -> hdfs sink
      cy.createConnections([
        perfSourceName,
        pipelineOnlyTopicName1,
        streamName,
        pipelineOnlyTopicName2,
        hdfsSinkName,
      ]);

      // Should create four links
      cy.get('#paper .joint-link').should('have.length', 4);
    });
  });

  context('Automatically topic creation', () => {
    it('should create a new topic between a source and sink connection', () => {
      const shabondiSourceName = generate.serviceName({ prefix: 'source' });
      const hdfsSinkName = generate.serviceName({ prefix: 'sink' });

      cy.addElements([
        {
          name: shabondiSourceName,
          kind: KIND.source,
          className: SOURCE.shabondi,
        },
        {
          name: hdfsSinkName,
          kind: KIND.sink,
          className: SINK.hdfs,
        },
      ]);

      cy.createConnections([shabondiSourceName, hdfsSinkName]);

      // It should automatically generate a "T1" topic and create two links
      cy.get('#paper').findByText('T1').should('exist');
      cy.get('#paper .joint-link').should('have.length', 2);
    });

    it('should create a new topic between a source and stream connection', () => {
      const jdbcSourceName = generate.serviceName({ prefix: 'source' });
      const streamName = generate.serviceName({ prefix: 'stream' });

      cy.addElements([
        {
          name: jdbcSourceName,
          kind: KIND.source,
          className: SOURCE.jdbc,
        },
        {
          name: streamName,
          kind: KIND.stream,
        },
      ]);

      cy.createConnections([jdbcSourceName, streamName]);

      // It should automatically generate a "T1" topic and create two links
      cy.get('#paper').findByText('T1').should('exist');
      cy.get('#paper .joint-link').should('have.length', 2);
    });

    it('should create a new topic between a stream and sink connection', () => {
      const streamName = generate.serviceName({ prefix: 'stream' });
      const shabondiSink = generate.serviceName({ prefix: 'source' });

      cy.addElements([
        {
          name: streamName,
          kind: KIND.stream,
        },
        {
          name: shabondiSink,
          kind: KIND.sink,
          className: SINK.shabondi,
        },
      ]);

      cy.createConnections([streamName, shabondiSink]);

      // It should automatically generate a "T1" topic and create two links
      cy.get('#paper').findByText('T1').should('exist');
      cy.get('#paper .joint-link').should('have.length', 2);
    });
  });
});
