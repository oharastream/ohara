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

import * as utils from '../utils';

const setup = () => {
  const nodeName = `node${utils.makeRandomStr()}`;
  const zookeeperClusterName = `zookeeper${utils.makeRandomStr()}`;
  const brokerClusterName = `broker${utils.makeRandomStr()}`;
  const workerClusterName = `worker${utils.makeRandomStr()}`;
  const connectorName = `connector${utils.makeRandomStr()}`;
  const topicName = `topic${utils.makeRandomStr()}`;

  cy.createNode({
    name: nodeName,
    port: 22,
    user: utils.makeRandomStr(),
    password: utils.makeRandomStr(),
  });

  cy.createZookeeper({
    name: zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startZookeeper(zookeeperClusterName);

  cy.createBroker({
    name: brokerClusterName,
    zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startBroker(brokerClusterName);

  cy.testCreateWorker({
    name: workerClusterName,
    brokerClusterName,
    nodeNames: [nodeName],
  });

  cy.startWorker(workerClusterName);

  cy.testCreateTopic({
    name: topicName,
    brokerClusterName,
  });

  cy.startTopic(topicName);

  cy.createConnector({
    className: 'com.island.ohara.connector.ftp.FtpSource',
    'connector.name': connectorName,
    name: connectorName,
    topicKeys: [{ group: 'default', name: topicName }],
    workerClusterName,
  }).as('createConnector');

  return {
    nodeName,
    zookeeperClusterName,
    brokerClusterName,
    workerClusterName,
    connectorName,
    topicName,
  };
};

describe('Connector API', () => {
  beforeEach(() => cy.deleteAllServices());

  it('createConnector', () => {
    const { connectorName, topicName } = setup();

    cy.get('@createConnector').then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(settings).to.be.a('object');
      expect(settings.className).to.be.a('string');
      expect(settings['connector.name']).to.eq(connectorName);
      expect(settings['tasks.max']).to.be.a('number');
      expect(settings.name).to.be.a('string');
      expect(settings.workerClusterName).to.be.a('string');
      expect(settings.topicKeys)
        .to.be.an('array')
        .to.have.lengthOf(1)
        .to.have.deep.property('[0].name', topicName);

      expect(settings.topicKeys).to.have.deep.property('[0].group', 'default');
    });
  });

  it('fetchConnector', () => {
    const { connectorName, topicName } = setup();

    cy.fetchConnector(connectorName).then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(result).to.include.keys('settings');
      expect(settings).to.be.an('object');
      expect(settings['className']).to.be.a('string');
      expect(settings['connector.name']).to.eq(connectorName);
      expect(settings['tasks.max']).to.be.a('number');
      expect(settings.name).to.be.a('string');
      expect(settings.workerClusterName).to.be.a('string');

      expect(settings.topicKeys)
        .to.be.an('array')
        .to.have.lengthOf(1)
        .to.have.deep.property('[0].name', topicName);

      expect(settings.topicKeys).to.have.deep.property('[0].group', 'default');
    });
  });

  it('updateConnector', () => {
    const { workerClusterName, connectorName, topicName } = setup();

    const params = {
      name: connectorName,
      params: {
        name: connectorName,
        author: 'root',
        columns: [
          { dataType: 'STRING', name: 'test', newName: 'test', order: 1 },
        ],
        className: 'com.island.ohara.connector.ftp.FtpSource',
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
        version: '0.7.2-SNAPSHOT',
        workerClusterName,
      },
    };

    cy.updateConnector(params).then(response => {
      const {
        data: { isSuccess, result },
      } = response;
      const { settings } = result;

      expect(isSuccess).to.eq(true);

      expect(settings).to.be.a('object');
      expect(settings.author).to.be.a('string');
      expect(settings.columns).to.be.a('array');
      expect(settings.className).to.be.a('string');
      expect(settings.name).to.be.a('string');
      expect(settings['connector.name']).to.be.a('string');
      expect(settings['ftp.completed.folder']).to.be.a('string');
      expect(settings['ftp.encode']).to.be.a('string');
      expect(settings['ftp.error.folder']).to.be.a('string');
      expect(settings['ftp.hostname']).to.be.a('string');
      expect(settings['ftp.input.folder']).to.be.a('string');
      expect(settings['ftp.port']).to.be.a('number');
      expect(settings['ftp.user.name']).to.be.a('string');
      expect(settings['ftp.user.password']).to.be.a('string');
      expect(settings.kind).to.be.a('string');
      expect(settings.revision).to.be.a('string');
      expect(settings['tasks.max']).to.be.a('number');
      expect(settings.version).to.be.a('string');
      expect(settings.workerClusterName).to.be.a('string');

      expect(settings.topicKeys)
        .to.be.an('array')
        .to.have.lengthOf(1)
        .to.have.deep.property('[0].name', topicName);

      expect(settings.topicKeys).to.have.deep.property('[0].group', 'default');
    });
  });

  it('startConnector', () => {
    const { connectorName } = setup();

    cy.startConnector(connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });

  it('stopConnector', () => {
    const { connectorName } = setup();

    cy.stopConnector(connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });

  it('deleteConnector', () => {
    const { connectorName } = setup();

    cy.deleteConnector(connectorName).then(response => {
      expect(response.data.isSuccess).to.eq(true);
    });
  });
});
