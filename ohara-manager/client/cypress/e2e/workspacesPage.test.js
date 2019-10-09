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

import { WORKSPACES } from '../../src/constants/urls';
import { CONNECTOR_FILTERS } from '../../src/constants/pipelines';
import { divide, floor } from 'lodash';
import * as generate from '../../src/utils/generate';

describe('WorkspacesPage', () => {
  before(() => {
    cy.removeWorkers();
    cy.addWorker();
  });

  beforeEach(() => {
    cy.server();
    cy.route('GET', 'api/workers').as('getWorkers');
    cy.route('GET', 'api/workers/*').as('getWorker');
    cy.route('GET', 'api/brokers/*').as('getBroker');
    cy.route('GET', 'api/zookeepers/*').as('getZookeeper');
    cy.route('GET', 'api/topics').as('getTopics');
    cy.route('GET', 'api/pipelines').as('getPipelines');
    cy.route('PUT', 'api/workers/*/stop').as('stopWorker');
    cy.route('PUT', 'api/workers/*/start').as('startWorker');
    cy.route('GET', 'api/files*').as('getFiles');
    cy.route('POST', 'api/zookeepers').as('createZookeeper');
    cy.route('POST', 'api/brokers').as('createBroker');
  });

  it('creates a workspace', () => {
    const nodeName = Cypress.env('nodeHost');
    const prefix = Cypress.env('servicePrefix');
    const workerName = generate.serviceName({
      prefix: `${prefix}wk`,
      length: 3,
    });

    cy.visit(WORKSPACES, {
      onBeforeLoad(win) {
        win.servicePrefix = prefix; // Add prefix for generated services
      },
    })
      .getByText('NEW WORKSPACE')
      .click()
      .getByPlaceholderText('cluster00')
      .type(workerName)
      .getByTestId(nodeName)
      .click()
      .uploadJar(
        'input[type=file]',
        'plugin/ohara-it-sink.jar',
        'ohara-it-sink.jar',
        'application/java-archive',
      )
      .wait(500)
      .getByText('ohara-it-sink')
      .click()
      .getByText('ADD')
      .click()
      .wait('@createZookeeper')
      .wait('@createBroker');

    cy.getByText(workerName, { timeout: 40000 }).should('have.length', 1);
  });

  it('adds and removes a topic', () => {
    const topicName = generate.serviceName({ prefix: 'topic' });

    cy.visit(WORKSPACES)
      .wait('@getWorkers')
      .getByTestId(Cypress.env('WORKER_NAME'))
      .click()
      .getByTestId('workspace-tab')
      .within(() => {
        cy.getByText('TOPICS').click();
      })
      .getByText('NEW TOPIC')
      .click()
      .getByPlaceholderText('Kafka Topic')
      .type(topicName)
      .getByTestId('partitions-input')
      .type(1)
      .getByTestId('replications-input')
      .type(1)
      .getByText('ADD')
      .click()
      .wait('@getTopics')
      .getByText(topicName)
      .should('have.length', 1);

    cy.getByTestId(topicName)
      .click({ force: true })
      .getByText('DELETE')
      .click()
      .getByText(`Successfully deleted the topic: ${topicName}`)
      .should('have.length', 1);
  });

  it('adds and removes a streamApp', () => {
    cy.visit(WORKSPACES)
      .wait('@getWorkers')
      .getByTestId(Cypress.env('WORKER_NAME'))
      .click()
      .getByTestId('workspace-tab')
      .within(() => {
        cy.getByText('STREAM JARS').click();
      })
      .wait('@getFiles')
      .uploadJar(
        'input[type=file]',
        'streamApp/ohara-streamapp.jar',
        'ohara-streamapp.jar',
        'application/java-archive',
      )
      .wait(500)
      .getByText('ohara-streamapp.jar')
      .should('have.length', 1);

    cy.getByTestId('ohara-streamapp.jar')
      .click()
      .getByText('DELETE')
      .click()
      .getByText('Successfully deleted the stream app!')
      .should('have.length', 1)
      .wait('@getFiles')
      .get('td')
      .should('have.length', 0);
  });

  it('should warn the user when a duplicate jar name is upload', () => {
    cy.visit(WORKSPACES)
      .wait('@getWorkers')
      .getByTestId(Cypress.env('WORKER_NAME'))
      .click()
      .getByTestId('workspace-tab')
      .within(() => {
        cy.getByText('STREAM JARS').click();
      })
      .wait('@getFiles')
      .uploadJar(
        'input[type=file]',
        'streamApp/ohara-streamapp.jar',
        'ohara-streamapp.jar',
        'application/java-archive',
      )
      .wait(500)
      .uploadJar(
        'input[type=file]',
        'streamApp/ohara-streamapp.jar',
        'ohara-streamapp.jar',
        'application/java-archive',
      )
      .wait(500)
      .getByText('The jar name already exists!')
      .should('have.length', 1)
      .queryAllByText('ohara-streamapp.jar')
      .should('have.length', 1);
  });

  it('should link to the correct service page', () => {
    cy.visit(WORKSPACES)
      .wait('@getWorkers')
      .getByTestId(Cypress.env('WORKER_NAME'))
      .click()
      .url()
      .should('include', '/overview')
      .getByTestId('overview-nodes-link')
      .click()
      .url()
      .should('include', '/nodes')
      .getByText('NEW NODE')
      .should('have.length', 1);

    cy.getByTestId('workspace-tab')
      .within(() => {
        cy.getByText('OVERVIEW').click();
      })
      .getByTestId('overview-topics-link')
      .click()
      .url()
      .should('include', '/topics')
      .getByText('NEW TOPIC')
      .should('have.length', 1);

    cy.getByTestId('workspace-tab')
      .within(() => {
        cy.getByText('OVERVIEW').click();
      })
      .getByTestId('overview-streamapps-link')
      .click()
      .url()
      .should('include', '/streamjars')
      .getByText('NEW JAR')
      .should('have.length', 1);

    cy.getByTestId('workspace-tab')
      .within(() => {
        cy.getByText('OVERVIEW').click();
      })
      .getByTestId('overview-plugins-link')
      .click()
      .url()
      .should('include', '/plugins')
      .getByText('ADD PLUGIN')
      .should('have.length', 1);
  });

  it('should display the overview info', () => {
    cy.addTopic().as('overviewTopic');

    cy.visit(WORKSPACES)
      .wait('@getWorkers')
      .uploadTestStreamAppJar(Cypress.env('WORKER_NAME'))
      .getByTestId(Cypress.env('WORKER_NAME'))
      .click()
      .wait('@getWorker')
      .then(xhr => {
        const {
          imageName,
          clientPort,
          nodeNames,
          jmxPort,
        } = xhr.response.body.settings;

        // Basic info
        cy.getByText(`Worker Image: ${imageName}`).should('have.length', 1);

        // Nodes
        nodeNames.forEach(node => {
          cy.getByText(`${node}:${clientPort}`)
            .should('have.length', 1)
            .getByTestId(`Worker-${node}:${clientPort}`)
            .trigger('mouseover')
            .getByText(`Jmxport: ${jmxPort}`)
            .should('have.length', 1);
        });

        const connectors = xhr.response.body.connectors.filter(
          connector => !CONNECTOR_FILTERS.includes(connector.className),
        );

        connectors.forEach(connector => {
          const name = connector.className.split('.').pop();

          cy.getByText(name)
            .should('have.length', 1)
            .getByTestId(`${name}-tooltip`)
            .trigger('mouseover')
            .getByText(connector.className)
            .should('have.length', 1);

          const definitions = connector.definitions;
          const keys = ['kind', 'version', 'author', 'class'];

          definitions
            .filter(definition => keys.includes(definition.key))
            .forEach((definition, index) => {
              cy.getByTestId(`${keys[index]}-value`).then($el =>
                cy.wrap($el.text()).should('have.eq', definition.defaultValue),
              );
            });

          cy.getByTestId(`${name}-tooltip`).trigger('mouseout');
        });
      })
      .wait('@getBroker')
      .then(xhr => {
        const {
          clientPort,
          nodeNames,
          jmxPort,
          exporterPort,
        } = xhr.response.body.settings;
        cy.getByText(
          `Broker Image: ${xhr.response.body.settings.imageName}`,
        ).should('have.length', 1);
        nodeNames.forEach(node => {
          cy.getByText(`${node}:${clientPort}`)
            .should('have.length', 1)
            .getByTestId(`Broker-${node}:${clientPort}`)
            .trigger('mouseover')
            .getByText(`Jmxport: ${jmxPort}`)
            .should('have.length', 1)
            .getByText(`Exporterport: ${exporterPort}`)
            .should('have.length', 1);
        });
      })
      .wait('@getZookeeper')
      .then(xhr => {
        const {
          clientPort,
          nodeNames,
          peerPort,
          electionPort,
        } = xhr.response.body.settings;
        cy.getByText(
          `Zookeeper Image: ${xhr.response.body.settings.imageName}`,
        ).should('have.length', 1);
        nodeNames.forEach(node => {
          cy.getByText(`${node}:${clientPort}`)
            .should('have.length', 1)
            .getByTestId(`Zookeeper-${node}:${clientPort}`)
            .trigger('mouseover')
            .getByText(`Peerport: ${peerPort}`)
            .should('have.length', 1)
            .getByText(`Electionport: ${electionPort}`)
            .should('have.length', 1)
            .getByTestId(`Zookeeper-${node}:${clientPort}`)
            .trigger('mouseout');
        });
      })
      .wait('@getTopics')
      .then(xhr => {
        const topics = xhr.response.body;
        cy.get('@overviewTopic').then(topic => {
          const currentBroker = topic.name;

          topics
            .filter(topic => {
              return topic.settings.brokerClusterKey.name === currentBroker;
            })
            .forEach(topic => {
              const { name, numberOfPartitions, numberOfReplications } = topic;
              cy.getByText(name)
                .should('have.length', 1)
                .getByTestId(`${name}-nop-${numberOfPartitions}`)
                .should('have.length', 1)
                .getByTestId(`${name}-nor-${numberOfReplications}`)
                .should('have.length', 1);
            });
        });
      })
      .wait('@getFiles')
      .then(xhr => {
        xhr.response.body.forEach(file => {
          const size = floor(divide(file.size, 1024), 1);
          cy.getByText(file.name)
            .should('have.length', 1)
            .getByText(String(size))
            .should('have.length', 1);
        });
      });
  });
});

describe('plugin', () => {
  let workerName;
  const jarName = 'ohara-it-sink.jar';
  before(() => {
    const prefix = Cypress.env('servicePrefix');
    workerName = generate.serviceName({
      prefix: `${prefix}wk`,
      length: 5,
    });
    cy.deleteTestPlugin({ jarName, workerName });
    cy.removeWorkers();
    cy.addWorker({ jarName, workerName });
  });

  beforeEach(() => {
    cy.server();
    cy.route('GET', 'api/workers').as('getWorkers');
    cy.route('GET', 'api/workers/*').as('getWorker');
    cy.route('GET', 'api/brokers/*').as('getBroker');
    cy.route('GET', 'api/zookeepers/*').as('getZookeeper');
    cy.route('GET', 'api/topics/*').as('getTopics');
    cy.route('GET', 'api/pipelines').as('getPipelines');
    cy.route('GET', 'api/connectors/*').as('getConnectors');
    cy.route('PUT', 'api/workers/*/stop').as('stopWorker');
    cy.route('PUT', 'api/workers/*/start').as('startWorker');
    cy.route('PUT', 'api/workers/*').as('putWorker');
    cy.route('PUT', 'api/connectors/*/start?*').as('startConnector');
    cy.route('PUT', 'api/connectors/*/stop?*').as('stopConnector');
    cy.route('GET', 'api/files').as('getFiles');
  });

  it('should update a workespace', () => {
    cy.visit(WORKSPACES)
      .wait('@getWorkers')
      .getByTestId(workerName)
      .click()
      .getByTestId('workspace-tab')
      .within(() => {
        cy.getByText('PLUGINS').click();
      })
      .wait('@getFiles')
      .uploadJar(
        'input[type=file]',
        'plugin/ohara-it-source.jar',
        'ohara-it-source.jar',
        'application/java-archive',
      )
      .wait('@getFiles')
      .getByText('Plugin successfully uploaded!')
      .should('have.length', 1)
      .queryAllByText('ohara-it-source.jar')
      .should('have.length', 1)
      .getByText(
        'You’ve made some changes to the plugins: 1 added. Please restart for these settings to take effect!!',
      )
      .should('have.length', 1)
      .getByText('RESTART')
      .click()
      .getByTestId('confirm-button-RESTART')
      .click()
      .wait('@getPipelines', { timeout: 60000 })
      .wait('@stopWorker', { timeout: 60000 })
      .wait('@startWorker', { timeout: 60000 })
      .wait('@getFiles', { timeout: 60000 })
      .getByTestId('plugins-loaded', { timeout: 60000 })
      .should('have.length', 1);
  });

  it('should faild to update a workespace', () => {
    const pipelineName = generate.serviceName({ prefix: 'pipeline' });
    const perfName = generate.serviceName({ prefix: 'perf' });
    const topicName = generate.serviceName({ prefix: 'topic' });
    const dumbsinkName = generate.serviceName({ prefix: 'dumbsink' });
    cy.addPipeline({
      name: pipelineName,
      group: `${workerName}${pipelineName}`,
      tags: {
        workerClusterName: workerName,
      },
    });
    cy.addTopic(topicName, workerName);
    cy.addConnector({
      'connector.class': 'com.island.ohara.connector.perf.PerfSource',
      group: `${workerName}${pipelineName}`,
      name: perfName,
      workerClusterKey: { name: workerName },
    });
    cy.putConnector({
      url: `/${perfName}?group=${workerName}${pipelineName}`,
      param: {
        topicKeys: [{ group: `${workerName}`, name: topicName }],
      },
    });
    cy.addConnector({
      'connector.class': 'com.island.ohara.it.connector.DumbSinkConnector',
      group: `${workerName}${pipelineName}`,
      name: dumbsinkName,
      workerClusterKey: { name: workerName },
    });
    cy.putConnector({
      url: `/${dumbsinkName}?group=${workerName}${pipelineName}`,
      param: {
        topicKeys: [{ group: `${workerName}`, name: topicName }],
      },
    });
    cy.putPipeline({
      url: `/${pipelineName}?group=${workerName}${pipelineName}`,
      param: {
        flows: [
          {
            from: {
              group: `${workerName}${pipelineName}`,
              name: perfName,
            },
            to: [
              {
                group: `${workerName}${pipelineName}`,
                name: topicName,
              },
            ],
          },
          {
            from: {
              group: `${workerName}${pipelineName}`,
              name: dumbsinkName,
            },
            to: [],
          },
          {
            from: {
              group: `${workerName}`,
              name: topicName,
            },
            to: [{ group: `${workerName}`, name: dumbsinkName }],
          },
        ],
      },
    });
    cy.putConnector({
      url: `/${perfName}/start?group=${workerName}${pipelineName}`,
    });
    cy.putConnector({
      url: `/${dumbsinkName}/start?group=${workerName}${pipelineName}`,
    });
    cy.visit(WORKSPACES)
      .wait('@getWorkers')
      .getByTestId(workerName)
      .click()
      .getByTestId('workspace-tab')
      .within(() => {
        cy.getByText('PLUGINS').click();
      })
      .wait('@getFiles')
      .getByTestId('ohara-it-sink.jar')
      .click()
      .getByText('RESTART')
      .click()
      .getByTestId('confirm-button-RESTART')
      .click()
      .wait('@getPipelines', { timeout: 60000 })
      .wait('@stopConnector', { timeout: 60000 })
      .wait('@stopConnector', { timeout: 60000 })
      .wait('@getConnectors', { timeout: 60000 })
      .wait('@stopWorker', { timeout: 60000 })
      .wait('@startWorker', { timeout: 60000 })
      .wait('@startConnector', { timeout: 60000 })
      .wait('@startConnector', { timeout: 60000 })
      .wait('@stopWorker', { timeout: 60000 })
      .wait('@putWorker', { timeout: 60000 })
      .wait('@startWorker', { timeout: 60000 })
      .wait('@startConnector', { timeout: 60000 })
      .wait('@startConnector', { timeout: 60000 })
      .wait('@getFiles', { timeout: 60000 })
      .getByTestId('ohara-it-sink.jar', { timeout: 60000 })
      .should('have.length', 1);
  });
});
