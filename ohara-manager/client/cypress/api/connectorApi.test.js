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

import * as generate from '../../src/utils/generate';

const setup = () => {
  const nodeName = generate.serviceName({ prefix: 'node' });
  const zookeeperClusterName = generate.serviceName({ prefix: 'zk' });
  const brokerClusterName = generate.serviceName({ prefix: 'bk' });
  const workerClusterName = generate.serviceName({ prefix: 'wk' });
  const connectorName = generate.serviceName({ prefix: 'connector' });
  const topicName = generate.serviceName({ prefix: 'topic' });
  const pipelineName = generate.serviceName({ prefix: 'pi' });

  const pipelineGroup = `${workerClusterName}${pipelineName}`;
  const topicGroup = workerClusterName;

  cy.createNode({
    name: nodeName,
    port: 22,
    user: generate.userName(),
    password: generate.password(),
  });

  cy.createZookeeper({
    name: zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startZookeeper(zookeeperClusterName);

  cy.createBroker({
    name: brokerClusterName,
    zookeeperClusterKey: {
      group: 'default',
      name: zookeeperClusterName,
    },
    nodeNames: [nodeName],
  });

  cy.startBroker(brokerClusterName);

  cy.createWorker({
    name: workerClusterName,
    brokerClusterKey: {
      group: 'default',
      name: brokerClusterName,
    },
    nodeNames: [nodeName],
  });

  cy.startWorker(workerClusterName);

  cy.createTopic({
    name: topicName,
    group: topicGroup,
    brokerClusterKey: {
      group: 'default',
      name: brokerClusterName,
    },
  });

  cy.startTopic(topicGroup, topicName);

  cy.createConnector({
    'connector.class': 'com.island.ohara.connector.ftp.FtpSource',
    'connector.name': connectorName,
    name: connectorName,
    topicKeys: [{ group: topicGroup, name: topicName }],
    workerClusterKey: {
      name: workerClusterName,
    },
    group: pipelineGroup,
    tags: { name: connectorName },
  }).as('createConnector');

  return {
    nodeName,
    zookeeperClusterName,
    brokerClusterName,
    workerClusterName,
    connectorName,
    topicName,
    topicGroup,
    pipelineGroup,
  };
};

describe('Connector API', () => {
  beforeEach(() => cy.deleteAllServices());

  it('createConnector', () => {
    const { topicGroup, pipelineGroup } = setup();

    cy.get('@createConnector').then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(settings).to.be.an('object');
      expect(settings.tags.name).to.eq(settings.name);

      expect(settings.group).to.eq(pipelineGroup);
      expect(settings.topicKeys).to.have.deep.property('[0].group', topicGroup);
    });
  });

  it('fetchConnector', () => {
    const { pipelineGroup, connectorName, topicName, topicGroup } = setup();

    cy.fetchConnector(pipelineGroup, connectorName).then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(result).to.include.keys('settings');
      expect(settings).to.be.an('object');
      expect(settings.tags.name).to.eq(settings.name);

      expect(settings.group).to.eq(pipelineGroup);
      expect(settings.topicKeys)
        .to.be.an('array')
        .to.have.lengthOf(1)
        .to.have.deep.property('[0].name', topicName);

      expect(settings.topicKeys).to.have.deep.property('[0].group', topicGroup);
    });
  });

  it('updateConnector', () => {
    const {
      workerClusterName,
      connectorName,
      topicName,
      topicGroup,
      pipelineGroup,
    } = setup();

    const params = {
      name: connectorName,
      params: {
        name: connectorName,
        author: 'root',
        columns: [
          { dataType: 'STRING', name: 'test', newName: 'test', order: 1 },
        ],
        'connector.class': 'com.island.ohara.connector.ftp.FtpSource',
        'connector.name': 'Untitled source',
        'ftp.completed.folder': 'test',
        'ftp.encode': 'UTF-8',
        'ftp.error.folder': 'test',
        'ftp.hostname': 'test',
        'ftp.input.folder': 'test',
        'ftp.port': 20,
        'ftp.user.name': 'test',
        'ftp.user.password': 'test',
        kind: 'source',
        revision: '1e7da9544e6aa7ad2f9f2792ed8daf5380783727',
        'tasks.max': 1,
        topicKeys: [{ group: 'default', name: topicName }],
        version: '0.8.2-SNAPSHOT',
        workerClusterKey: {
          group: 'default',
          name: workerClusterName,
        },
      },
    };

    cy.updateConnector({
      name: connectorName,
      group: pipelineGroup,
      params,
    }).then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(settings).to.be.a('object');
      expect(settings.tags.name).to.eq(settings.name);

      expect(settings.group).to.eq(pipelineGroup);
      expect(settings.topicKeys)
        .to.be.an('array')
        .to.have.lengthOf(1)
        .to.have.deep.property('[0].name', topicName);

      expect(settings.topicKeys).to.have.deep.property('[0].group', topicGroup);
    });
  });

  it('startConnector', () => {
    const { connectorName, pipelineGroup } = setup();

    cy.startConnector(pipelineGroup, connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });

  it('stopConnector', () => {
    const { connectorName, pipelineGroup } = setup();

    cy.stopConnector(pipelineGroup, connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });

  it('deleteConnector', () => {
    const { connectorName, pipelineGroup } = setup();

    cy.deleteConnector(pipelineGroup, connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });
});
