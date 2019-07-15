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

import 'cypress-testing-library/add-commands';
import { axiosInstance } from '../../src/api/apiUtils';
import * as utils from '../utils';

Cypress.Commands.add('registerWorker', workerName => {
  const fileName = '../scripts/servicesApi/service.json';
  const update = { name: workerName, serviceType: 'workers' };

  cy.task('readFileMaybe', fileName).then(data => {
    // Append a new worker to the existing file
    cy.writeFile(fileName, [...data, update]);
  });
});

Cypress.Commands.add('createWorker', () => {
  cy.log('Create a new worker');

  const { name: nodeName } = utils.getFakeNode();
  const workerName = 'wk' + utils.makeRandomStr();
  const groupId = utils.makeRandomStr();
  const configTopicName = 'tp' + utils.makeRandomStr();
  const offsetTopicName = 'otp' + utils.makeRandomStr();
  const statusTopicName = 'stp' + utils.makeRandomStr();

  // Store the worker names in a file as well as
  // in the Cypress env as we'll be using them in the tests
  Cypress.env('WORKER_NAME', workerName);
  cy.registerWorker(workerName);

  cy.request('GET', 'api/brokers')
    .then(res => res.body[0]) // there should only be one broker in the list
    .as('broker');

  cy.get('@broker').then(broker => {
    cy.request('POST', 'api/workers', {
      name: workerName,
      imageName: 'oharastream/connect-worker:0.6.1-SNAPSHOT',
      clientPort: utils.makeRandomPort(),
      jmxPort: utils.makeRandomPort(),
      brokerClusterName: broker.name,
      jars: [],
      groupId: groupId,
      configTopicName: configTopicName,
      offsetTopicName: offsetTopicName,
      statusTopicName: statusTopicName,
      nodeNames: [nodeName],
    });
  });

  let count = 0;
  const max = 10;
  // Make a request to configurator see if worker cluster is ready for use
  const req = endPoint => {
    cy.request('GET', endPoint).then(res => {
      // When connectors field has the right connector info
      // this means that everything is ready to be tested
      if (res.body.some(worker => worker.name === workerName)) return;

      // Add a maximum loop time to prevent from running into an infinite loop
      if (count > max) return;

      // if worker is not ready yet, wait a 1.5 sec and make another request
      count++;
      cy.wait(1500);
      req(endPoint);
    });
  };

  const endPoint = `api/workers`;
  cy.request('GET', endPoint).then(() => req(endPoint));
});

Cypress.Commands.add('createPipeline', pipeline => {
  cy.request('POST', `/api/pipelines`, {
    name: pipeline.name || 'Untitled pipeline',
    rules: pipeline.rules || {},
    workerClusterName: pipeline.workerName,
    ...pipeline,
  }).then(({ body }) => body);
});

Cypress.Commands.add('createTopic', overrides => {
  cy.request('GET', 'api/brokers')
    .then(res => res.body[0]) // there should only be one broker in the list
    .as('broker');

  cy.get('@broker').then(broker => {
    cy.request('POST', '/api/topics', {
      name: utils.makeRandomStr(),
      numberOfReplications: 1,
      brokerClusterName: broker.name,
      numberOfPartitions: 1,
      ...overrides,
    }).then(({ body }) => body); // we'll need the returned data later on
  });
});

Cypress.Commands.add('deleteAllWorkers', () => {
  cy.log('Delete all previous created workers');

  const fileName = '../scripts/servicesApi/service.json';

  cy.task('readFileMaybe', fileName).then(data => {
    if (!data) return; // File is not there, skip the whole process

    const workerNames = data
      .filter(d => d.serviceType === 'workers')
      .map(d => d.name);

    cy.request('GET', 'api/workers').then(res => {
      const targetWorkers = res.body.filter(worker =>
        workerNames.includes(worker.name),
      );

      if (targetWorkers.length > 0) {
        targetWorkers.forEach(targetWorker => {
          const { name } = targetWorker;
          cy.request('DELETE', `api/workers/${name}?force=true`).then(() =>
            utils.recursiveDeleteWorker('api/workers', name),
          );
        });
      }
    });
  });
});

Cypress.Commands.add('insertNode', node => {
  Cypress.log({
    name: 'INSERT_NODE',
  });

  cy.request('POST', 'api/nodes', {
    name: node.name,
    port: node.port,
    user: node.user,
    password: node.password,
  });
});

Cypress.Commands.add('deleteAllNodes', () => {
  Cypress.log({
    name: 'DELETE_ALL_NODES',
  });

  const _ = Cypress._;

  cy.request('GET', 'api/nodes')
    .then(res => res.body)
    .then(nodes => {
      if (!_.isEmpty(nodes)) {
        _.forEach(nodes, node => {
          cy.request('DELETE', `api/nodes/${node.name}`);
        });
      }
    });
});

Cypress.Commands.add('deleteTopic', topicName => {
  cy.request('GET', 'api/topics').then(res => {
    res.body.forEach(({ name, id }) => {
      if (name === topicName) {
        cy.request('DELETE', `api/topics/${id}`);
      }
    });
  });
});

Cypress.Commands.add('deletePipeline', pipelineName => {
  cy.request('GET', 'api/pipelines').then(res => {
    res.body.forEach(({ name, id }) => {
      if (name === pipelineName) {
        cy.request('DELETE', `api/pipelines/${id}`);
      }
    });
  });
});

Cypress.Commands.add('deleteAllPipeline', pipelineName => {
  cy.request('GET', 'api/pipelines').then(res => {
    res.body.forEach(({ name, id }) => {
      cy.request({
        method: 'DELETE',
        url: `api/pipelines/${id}`,
        retryOnStatusCodeFailure: true,
      });
    });
  });
});

Cypress.Commands.add(
  'deleteStreamApp',
  (
    streamAppName = 'ohara-streamapp.jar',
    workerName = Cypress.env('WORKER_NAME'),
  ) => {
    cy.request('GET', 'api/stream/jars').then(res => {
      res.body.forEach(({ name, workerClusterName, id }) => {
        const isTarget =
          name === streamAppName && workerName === workerClusterName;
        if (isTarget) {
          cy.request('DELETE', `api/stream/jars/${id}`);
        }
      });
    });
  },
);

Cypress.Commands.add('uploadStreamAppJar', () => {
  cy.getByTestId('toolbar-streams')
    .click()
    .uploadJar(
      'input[type=file]',
      'streamApp/ohara-streamapp.jar',
      'ohara-streamapp.jar',
      'application/java-archive',
    )
    .wait(500);
});

Cypress.Commands.add('uploadTestStreamAppJar', wk => {
  cy.log(wk);
  cy.fixture(`streamApp/ohara-streamapp.jar`, 'base64')
    .then(Cypress.Blob.base64StringToBlob)
    .then(blob => {
      const type = 'application/java-archive';
      const url = '/api/jars';
      const config = {
        headers: {
          'content-type': 'multipart/form-data',
        },
      };
      const testFile = new File([blob], 'ohara-streamapp.jar', { type: type });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(testFile);
      blob = dataTransfer.files;
      let formData = new FormData();
      formData.append('jar', blob[0]);
      formData.append('group', wk);
      const res = axiosInstance.post(url, formData, config);
      cy.log(res);
    });
});

Cypress.Commands.add('uploadJar', (selector, fixturePath, name, type) => {
  cy.get(selector).then(subject =>
    cy.window().then(win =>
      cy
        .fixture(fixturePath, 'base64')
        .then(Cypress.Blob.base64StringToBlob)
        .then(blob => {
          const el = subject[0];
          const testFile = new win.File([blob], name, {
            type,
          });
          const dataTransfer = new win.DataTransfer();
          dataTransfer.items.add(testFile);
          el.files = dataTransfer.files;
          cy.wrap(subject).trigger('change', { force: true });
        }),
    ),
  );
});
