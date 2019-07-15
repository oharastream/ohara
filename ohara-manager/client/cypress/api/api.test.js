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

import { makeRandomPort } from '../utils';

let brokerClusterName = '';
let jar = {};
let jarGroup = '';
let pipelineId = '';
let pipelineName = '';
let topicId = '';
let propertyId = '';
let fakeWorkerName = '';
const nodeName = `node${makeRandomPort()}`;
const wkName = `wk${makeRandomPort()}`;
const connectorName = `source${makeRandomPort()}`;

describe('Nodes', () => {
  it('createNode', () => {
    const data = {
      name: nodeName,
      port: 22,
      user: 'ohara',
      password: '123',
    };
    cy.createNode(data).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'name',
        'password',
        'port',
        'user',
        'services',
      );
      expect(data.result.services).to.be.a('array');
      expect(data.result.services[0]).to.include.keys('name', 'clusterNames');
      expect(data.result.services[1]).to.include.keys('name', 'clusterNames');
      expect(data.result.services[2]).to.include.keys('name', 'clusterNames');
      expect(data.result.services[0].name).to.eq('zookeeper');
      expect(data.result.services[1].name).to.eq('broker');
      expect(data.result.services[2].name).to.eq('connect-worker');
      expect(data.result.name).to.be.a('string');
      expect(data.result.password).to.be.a('string');
      expect(data.result.port).to.be.a('number');
      expect(data.result.user).to.be.a('string');
      expect(data.result.services[0].name).to.be.a('string');
      expect(data.result.services[0].clusterNames).to.be.a('array');
      expect(data.result.services[1].name).to.be.a('string');
      expect(data.result.services[1].clusterNames).to.be.a('array');
      expect(data.result.services[2].name).to.be.a('string');
      expect(data.result.services[2].clusterNames).to.be.a('array');
    });
  });
  it('updateNode', () => {
    const data = {
      name: nodeName,
      port: 23,
      user: 'ohara123',
      password: '1234',
    };
    cy.updateNode(data).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'name',
        'password',
        'port',
        'user',
        'services',
      );
      expect(data.result.port).to.eq(23);
      expect(data.result.user).to.eq('ohara123');
      expect(data.result.password).to.eq('1234');
      expect(data.result.services).to.be.a('array');
      expect(data.result.services[0]).to.include.keys('name', 'clusterNames');
      expect(data.result.services[1]).to.include.keys('name', 'clusterNames');
      expect(data.result.services[2]).to.include.keys('name', 'clusterNames');
      expect(data.result.services[0].name).to.eq('zookeeper');
      expect(data.result.services[1].name).to.eq('broker');
      expect(data.result.services[2].name).to.eq('connect-worker');
      expect(data.result.name).to.be.a('string');
      expect(data.result.password).to.be.a('string');
      expect(data.result.port).to.be.a('number');
      expect(data.result.user).to.be.a('string');
      expect(data.result.services[0].name).to.be.a('string');
      expect(data.result.services[0].clusterNames).to.be.a('array');
      expect(data.result.services[1].name).to.be.a('string');
      expect(data.result.services[1].clusterNames).to.be.a('array');
      expect(data.result.services[2].name).to.be.a('string');
      expect(data.result.services[2].clusterNames).to.be.a('array');
    });
  });
  it('fetchNodes', () => {
    cy.fetchNodes().then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.be.a('array');
      expect(data.result[0]).to.include.keys(
        'name',
        'password',
        'port',
        'user',
        'services',
      );
      expect(data.result[0].services).to.be.a('array');
      expect(data.result[0].services[0]).to.include.keys(
        'name',
        'clusterNames',
      );
      expect(data.result[0].services[1]).to.include.keys(
        'name',
        'clusterNames',
      );
      expect(data.result[0].services[2]).to.include.keys(
        'name',
        'clusterNames',
      );
      expect(data.result[0].services[0].name).to.eq('zookeeper');
      expect(data.result[0].services[1].name).to.eq('broker');
      expect(data.result[0].services[2].name).to.eq('connect-worker');
      expect(data.result[0].name).to.be.a('string');
      expect(data.result[0].password).to.be.a('string');
      expect(data.result[0].port).to.be.a('number');
      expect(data.result[0].user).to.be.a('string');
      expect(data.result[0].services[0].name).to.be.a('string');
      expect(data.result[0].services[0].clusterNames).to.be.a('array');
      expect(data.result[0].services[1].name).to.be.a('string');
      expect(data.result[0].services[1].clusterNames).to.be.a('array');
      expect(data.result[0].services[2].name).to.be.a('string');
      expect(data.result[0].services[2].clusterNames).to.be.a('array');
    });
  });
});

describe('Zookeepers', () => {
  it('fetchZookeepers', () => {
    cy.fetchZookeepers().then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result[0]).to.include.keys('clientPort', 'name', 'nodeNames');
      expect(data.result[0].clientPort).to.be.a('number');
      expect(data.result[0].name).to.be.a('string');
      expect(data.result[0].nodeNames).to.be.a('array');
    });
  });
});

describe('Brokers', () => {
  it('fetchBrokers', () => {
    cy.fetchBrokers().then(res => {
      const { data } = res;
      brokerClusterName = data.result[0].name;
      expect(data.isSuccess).to.eq(true);
      expect(data.result[0]).to.include.keys('clientPort', 'name', 'nodeNames');
      expect(data.result[0].clientPort).to.be.a('number');
      expect(data.result[0].name).to.be.a('string');
      expect(data.result[0].nodeNames).to.be.a('array');
    });
  });
});

describe('Jars', () => {
  const testJarName = 'ohara-it-source.jar';
  it('createJar', () => {
    cy.createJar(testJarName).then(res => {
      const { data } = res;
      jar = {
        name: data.result.name,
        group: data.result.group,
      };
      jarGroup = data.result.group;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys('name', 'group');
      expect(data.result.name).to.be.a('string');
      expect(data.result.group).to.be.a('string');
    });
  });
  it('fetchJars', () => {
    cy.fetchJars(jarGroup).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.be.a('array');
      expect(data.result[0]).to.include.keys('name', 'group');
      expect(data.result[0].name).to.be.a('string');
      expect(data.result[0].group).to.be.a('string');
    });
  });
});

describe('Workers', () => {
  it('createWorker', () => {
    const data = {
      name: wkName,
      jmxPort: makeRandomPort(),
      brokerClusterName: brokerClusterName,
      clientPort: makeRandomPort(),
      nodeNames: [nodeName],
      plugins: [jar],
    };
    cy.testCreateWorker(data).then(res => {
      cy.log(res);
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'name',
        'clientPort',
        'nodeNames',
        'connectors',
        'jarInfos',
        'configTopicName',
        'offsetTopicName',
        'statusTopicName',
      );
      expect(data.result.name).to.be.a('string');
      expect(data.result.clientPort).to.be.a('number');
      expect(data.result.nodeNames).to.be.a('array');
      expect(data.result.connectors).to.be.a('array');
      expect(data.result.jarInfos).to.be.a('array');
      expect(data.result.configTopicName).to.be.a('string');
      expect(data.result.offsetTopicName).to.be.a('string');
      expect(data.result.statusTopicName).to.be.a('string');
    });
  });

  it('fetchWorker', () => {
    cy.fetchWorker(wkName).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result.name).to.be.a('string');
      expect(data.result.clientPort).to.be.a('number');
      expect(data.result.nodeNames).to.be.a('array');
      expect(data.result.connectors).to.be.a('array');
      expect(data.result.jarInfos).to.be.a('array');
    });
  });

  it('fetchWorkers', () => {
    cy.fetchWorkers().then(res => {
      const { data } = res;
      fakeWorkerName = data.result[0].name;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.be.a('array');
      expect(data.result[0]).to.include.keys(
        'name',
        'nodeNames',
        'configTopicName',
        'offsetTopicName',
        'statusTopicName',
      );
      expect(data.result[0].name).to.be.a('string');
      expect(data.result[0].nodeNames).to.be.a('array');
      expect(data.result[0].configTopicName).to.be.a('string');
      expect(data.result[0].offsetTopicName).to.be.a('string');
      expect(data.result[0].statusTopicName).to.be.a('string');
    });
  });
});

describe('Topics', () => {
  const tpName = `tp${makeRandomPort()}`;

  it('CreateTopic', () => {
    const data = {
      name: tpName,
      numberOfPartitions: 1,
      brokerClusterName: brokerClusterName,
      numberOfReplications: 1,
    };
    cy.testCreateTopic(data).then(res => {
      const { data } = res;
      topicId = data.result.id;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'id',
        'numberOfPartitions',
        'numberOfReplications',
      );
      expect(data.result.id).to.be.a('string');
      expect(data.result.numberOfPartitions).to.be.a('number');
      expect(data.result.numberOfReplications).to.be.a('number');
    });
  });

  it('fetchTopic', () => {
    cy.fetchTopic(tpName).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'id',
        'numberOfPartitions',
        'numberOfReplications',
      );
      expect(data.result.id).to.be.a('string');
      expect(data.result.numberOfPartitions).to.be.a('number');
      expect(data.result.numberOfReplications).to.be.a('number');
    });
  });

  it('fetchTopics', () => {
    cy.fetchTopics().then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.be.a('array');
      expect(data.result[0]).to.include.keys(
        'id',
        'numberOfPartitions',
        'numberOfReplications',
      );
      expect(data.result[0].id).to.be.a('string');
      expect(data.result[0].numberOfPartitions).to.be.a('number');
      expect(data.result[0].numberOfReplications).to.be.a('number');
    });
  });
});

describe.skip('Pipelines', () => {
  it('createPipeline', () => {
    const data = {
      name: 'fakePipeline',
      rules: {},
      workerClusterName: wkName,
    };
    cy.testCreatePipeline(data).then(res => {
      const { data } = res;
      pipelineId = data.result.id;
      pipelineName = data.result.name;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'id',
        'name',
        'workerClusterName',
        'objects',
      );
      expect(data.result.id).to.be.a('string');
      expect(data.result.name).to.be.a('string');
      expect(data.result.workerClusterName).to.be.a('string');
      expect(data.result.objects).to.be.a('array');
    });
  });

  it('fetchPipeline', () => {
    cy.fetchPipeline(pipelineId).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'id',
        'name',
        'workerClusterName',
        'objects',
      );
      expect(data.result.id).to.be.a('string');
      expect(data.result.name).to.be.a('string');
      expect(data.result.workerClusterName).to.be.a('string');
      expect(data.result.objects).to.be.a('array');
    });
  });

  it('fetchPipelines', () => {
    cy.fetchPipelines().then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.be.a('array');
      expect(data.result[0]).to.include.keys(
        'id',
        'name',
        'workerClusterName',
        'objects',
      );
      expect(data.result[0].id).to.be.a('string');
      expect(data.result[0].name).to.be.a('string');
      expect(data.result[0].workerClusterName).to.be.a('string');
      expect(data.result[0].objects).to.be.a('array');
    });
  });

  it('updatePipeline', () => {
    const data = {
      id: pipelineId,
      params: {
        name: pipelineName,
        rules: {
          [topicId]: [],
        },
        workerClusterName: wkName,
      },
    };
    cy.updatePipeline(data).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'id',
        'name',
        'workerClusterName',
        'objects',
        'rules',
      );
      expect(data.result.id).to.be.a('string');
      expect(data.result.name).to.be.a('string');
      expect(data.result.workerClusterName).to.be.a('string');
      expect(data.result.objects).to.be.a('array');
      expect(data.result.rules).to.be.a('object');
      expect(data.result.objects[0].id).to.eq(topicId);
      expect(data.result.objects[0].kind).to.eq('topic');
      expect(data.result.objects[0].name).to.eq(topicId);
    });
  });

  it('deletePipeline', () => {
    cy.testDeletePipeline(pipelineId).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
    });
  });
});

describe('Connectors', () => {
  it('createConnector', () => {
    const data = {
      className: 'com.island.ohara.connector.ftp.FtpSource',
      configs: {},
      'connector.name': connectorName,
      name: connectorName,
      numberOfTasks: 1,
      schema: [],
      topics: [],
      workerClusterName: fakeWorkerName,
    };
    cy.createConnector(data).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys('settings');
      expect(data.result.settings).to.include.keys(
        'className',
        'connector.name',
        'name',
        'tasks.max',
        'workerClusterName',
      );
      expect(data.result.settings).to.be.a('object');
      expect(data.result.settings.className).to.be.a('string');
      expect(data.result.settings['connector.name']).to.be.a('string');
      expect(data.result.settings['tasks.max']).to.be.a('number');
      expect(data.result.settings.name).to.be.a('string');
      expect(data.result.settings.workerClusterName).to.be.a('string');
    });
  });

  it('fetchConnector', () => {
    cy.fetchConnector(connectorName).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys('settings');
      expect(data.result.settings).to.include.keys(
        'className',
        'connector.name',
        'name',
        'tasks.max',
        'workerClusterName',
      );
      expect(data.result.settings).to.be.a('object');
      expect(data.result.settings['className']).to.be.a('string');
      expect(data.result.settings['connector.name']).to.be.a('string');
      expect(data.result.settings['tasks.max']).to.be.a('number');
      expect(data.result.settings.name).to.be.a('string');
      expect(data.result.settings.workerClusterName).to.be.a('string');
    });
  });

  it('updateConnector', () => {
    const data = {
      id: connectorName,
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
        topics: [topicId],
        version: '0.6.1-SNAPSHOT',
        workerClusterName: fakeWorkerName,
      },
    };
    cy.updateConnector(data).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys('settings');
      expect(data.result.settings).to.be.a('object');
      expect(data.result.settings).to.include.keys(
        'author',
        'columns',
        'className',
        'connector.name',
        'ftp.completed.folder',
        'ftp.encode',
        'ftp.error.folder',
        'ftp.hostname',
        'ftp.input.folder',
        'ftp.port',
        'ftp.user.name',
        'ftp.user.password',
        'kind',
        'revision',
        'tasks.max',
        'topics',
        'version',
        'workerClusterName',
      );
      expect(data.result.settings.author).to.be.a('string');
      expect(data.result.settings.columns).to.be.a('array');
      expect(data.result.settings.className).to.be.a('string');
      expect(data.result.settings.name).to.be.a('string');
      expect(data.result.settings['connector.name']).to.be.a('string');
      expect(data.result.settings['ftp.completed.folder']).to.be.a('string');
      expect(data.result.settings['ftp.encode']).to.be.a('string');
      expect(data.result.settings['ftp.error.folder']).to.be.a('string');
      expect(data.result.settings['ftp.hostname']).to.be.a('string');
      expect(data.result.settings['ftp.input.folder']).to.be.a('string');
      expect(data.result.settings['ftp.port']).to.be.a('number');
      expect(data.result.settings['ftp.user.name']).to.be.a('string');
      expect(data.result.settings['ftp.user.password']).to.be.a('string');
      expect(data.result.settings.kind).to.be.a('string');
      expect(data.result.settings.revision).to.be.a('string');
      expect(data.result.settings['tasks.max']).to.be.a('number');
      expect(data.result.settings.topics).to.be.a('array');
      expect(data.result.settings.version).to.be.a('string');
      expect(data.result.settings.workerClusterName).to.be.a('string');
    });
  });

  it('startConnector', () => {
    cy.startConnector(connectorName).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys('id', 'settings', 'state');
      expect(data.result.state).to.be.a('string');
    });
  });

  it('stopConnector', () => {
    cy.stopConnector(connectorName).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys('id', 'settings');
    });
  });

  it('deleteConnector', () => {
    cy.deleteConnector(connectorName).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
    });
  });
});

describe('Streamapps', () => {
  it('createProperty', () => {
    const params = {
      jar: jar,
      name: 'streamapp',
    };
    cy.createProperty(params).then(res => {
      const { data } = res;
      propertyId = data.result.name;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'name',
        'instances',
        'jar',
        'from',
        'to',
      );
      expect(data.result.name).to.be.a('string');
      expect(data.result.instances).to.be.a('number');
      expect(data.result.from).to.be.a('array');
      expect(data.result.to).to.be.a('array');
      expect(data.result.jar).to.be.a('object');
      expect(data.result.jar).to.include.keys('name', 'group');
    });
  });

  it('fetchProperty', () => {
    cy.fetchProperty(propertyId).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'instances',
        'jar',
        'name',
        'from',
        'to',
      );
      expect(data.result.instances).to.be.a('number');
      expect(data.result.name).to.be.a('string');
      expect(data.result.from).to.be.a('array');
      expect(data.result.to).to.be.a('array');
      expect(data.result.jar).to.be.a('object');
      expect(data.result.jar).to.include.keys('name', 'group');
    });
  });

  it('updateProperty', () => {
    const params = {
      name: propertyId,
      from: [topicId],
      instances: 1,
    };
    cy.updateProperty(params).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys(
        'instances',
        'jar',
        'name',
        'from',
        'to',
      );
      expect(data.result.instances).to.be.a('number');
      expect(data.result.name).to.be.a('string');
      expect(data.result.from).to.be.a('array');
      expect(data.result.to).to.be.a('array');
      expect(data.result.jar).to.be.a('object');
      expect(data.result.jar).to.include.keys('name', 'group');
      expect(data.result.from[0]).to.eq(topicId);
    });
  });

  it('stopStreamApp', () => {
    cy.stopStreamApp(propertyId).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
    });
  });

  it('deleteProperty', () => {
    cy.deleteProperty(propertyId).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
    });
  });
});

describe('Logs', () => {
  it('fetchLogs', () => {
    cy.fetchLogs('workers', wkName).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.include.keys('name', 'logs');
      expect(data.result.name).to.be.a('string');
      expect(data.result.logs).to.be.a('array');
      expect(data.result.logs[0]).to.include.keys('name', 'value');
      expect(data.result.logs[0].name).to.be.a('string');
      expect(data.result.logs[0].value).to.be.a('string');
    });
  });
});

describe('Validates', () => {
  it.skip('validateConnector', () => {
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
      topics: [topicId],
      version: '0.6.1-SNAPSHOT',
      workerClusterName: fakeWorkerName,
    };
    cy.log(params);
    cy.validateConnector(params).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result.errorCount).to.eq(0);
      expect(data.result).to.include.keys('errorCount', 'settings');
      expect(data.result.settings).to.be.a('array');
    });
  });
});

describe('containers', () => {
  it('fetchContainers', () => {
    cy.fetchContainers(wkName).then(res => {
      const { data } = res;
      expect(data.isSuccess).to.eq(true);
      expect(data.result).to.be.a('array');
      expect(data.result[0]).include.keys('containers');
      expect(data.result[0].containers).to.be.a('array');
      expect(data.result[0].containers[0]).include.keys('state');
      expect(data.result[0].containers[0].state).to.be.a('string');
    });
  });
});
