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
    zookeeperClusterName: zookeeperClusterName,
    nodeNames: [nodeName],
  });

  cy.startBroker(brokerClusterName);

  cy.testCreateWorker({
    name: workerClusterName,
    brokerClusterName,
    nodeNames: [nodeName],
  }).as('testCreateWorker');

  cy.startWorker(workerClusterName);

  return {
    workerClusterName,
  };
};

describe('Validate API', () => {
  before(() => cy.deleteAllServices());

  it('validateConnector', () => {
    const { workerClusterName } = setup();

    const params = {
      author: 'root',
      columns: [
        { dataType: 'STRING', name: 'test', newName: 'test', order: 1 },
      ],
      name: 'source',
      'connector.class': 'com.island.ohara.connector.ftp.FtpSource',
      'connector.name': 'source',
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
      topics: ['topicName'],
      version: '0.7.2-SNAPSHOT',
      workerClusterName,
    };

    cy.validateConnector(params).then(response => {
      cy.log(response);
      const {
        data: { isSuccess, result },
      } = response;

      const { errorCount, settings } = result;

      expect(isSuccess).to.eq(true);

      expect(errorCount).to.eq(0);
      expect(settings).to.be.a('array');
    });
  });
});
