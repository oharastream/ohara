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

// eslint-disable-next-line @typescript-eslint/triple-slash-reference
///<reference path="../global.d.ts" />

import '@testing-library/cypress/add-commands';
import * as generate from '../../src/utils/generate';
import { some } from 'lodash';
import { NodeRequest } from '../../src/api/apiInterface/nodeInterface';
import { State } from '../../src/api/apiInterface/topicInterface';
import * as nodeApi from '../../src/api/nodeApi';
import {
  deleteAllServices,
  generateNodeIfNeeded,
  createServicesInNodes,
} from '../utils';
import { SettingSection } from '../types';

export interface CreateWorkspaceOption {
  workspaceName?: string;
  node?: NodeRequest;
  closeOnFailureOrFinish?: boolean;
}

export type CreateWorkspaceByApiOption = Omit<
  CreateWorkspaceOption,
  'closeOnFailureOrFinish'
>;

// Utility commands
Cypress.Commands.add('createJar', (file: Cypress.FixtureRequest) => {
  const { fixturePath, name, group: jarGroup, tags: jarTags } = file;
  cy.fixture(`${fixturePath}/${name}`, 'base64')
    .then(Cypress.Blob.base64StringToBlob)
    .then((blob) => {
      const blobObj = blob;
      const type = 'application/java-archive';
      const testFile = new File([blobObj], name, { type });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(testFile);
      const fileList = dataTransfer.files;
      const params: Cypress.FixtureResponse = {
        name,
        fileList,
        file: fileList[0],
        group: jarGroup,
      };
      if (jarTags) params.tags = jarTags;
      return params;
    });
});

Cypress.Commands.add('createNode', (node: NodeRequest = generate.node()) => {
  // if the intro dialog appears, we should close it
  cy.get('body').then(($body) => {
    if ($body.find('[data-testid="intro-dialog"]').length > 0) {
      cy.findByTestId('close-intro-button').filter(':visible').click();
    }
  });

  cy.findByTestId('nodes-dialog-open-button').click();
  cy.findByTestId('nodes-dialog').should('exist');

  cy.findByRole('table').then(($table) => {
    if ($table.find(`td:contains(${node.hostname})`).length === 0) {
      cy.findByTitle('Create Node').click();
      cy.findByLabelText(/hostname/i).type(node.hostname);
      cy.findByLabelText(/port/i).type(`${node.port}`);
      cy.findByLabelText(/user/i).type(node.user);
      cy.findByLabelText(/password/i).type(node.password);
      cy.findByText('CREATE').click();
    }
  });

  cy.findByTestId('nodes-dialog-close-button').click();
  cy.findByTestId('nodes-dialog').should('not.exist');

  return cy.wrap(node);
});

Cypress.Commands.add('createNodeIfNotExists', (nodeToCreate: NodeRequest) => {
  cy.request('api/nodes')
    .then((res) => res.body)
    .then((nodes) => {
      const isExist = some(nodes, {
        hostname: nodeToCreate.hostname,
      });
      if (isExist) {
        return cy.wrap(nodeToCreate);
      } else {
        // create if the node does not exist
        return cy
          .request('POST', 'api/nodes', nodeToCreate)
          .then((createdNode) => {
            return cy.wrap(createdNode);
          });
      }
    });
});

Cypress.Commands.add('deleteNode', (hostname, isInsideNodeList = false) => {
  if (isInsideNodeList) return removeNode();

  // Open node list
  cy.findByTitle(/node list/i)
    .should('exist')
    .click();

  cy.findByText(hostname).then(($el) => {
    if ($el.length > 0) {
      removeNode();
    }
  });

  cy.findByText(hostname).should('not.exist');

  function removeNode() {
    cy.findByTestId(`delete-node-${hostname}`).should('be.visible').click();
    cy.findByTestId('delete-dialog').findByText('DELETE').click();
    cy.findByText(hostname).should('not.exist');
  }
});

Cypress.Commands.add('deleteNodesByApi', () => {
  cy.wrap(null, { log: false }).then(async () => {
    const { data: nodes } = await nodeApi.getAll();
    await Promise.all(nodes.map((node) => nodeApi.remove(node.hostname)));

    Cypress.log({
      name: 'deleteNodesByApi',
      displayName: `Command`,
      message: `All nodes were deleted!`,
      consoleProps: () => ({ ...nodes }),
    });
  });
});

Cypress.Commands.add('addNode', (node = generateNodeIfNeeded) => {
  cy.get('body').then(($body) => {
    // the node has not been added yet, added directly
    if ($body.find(`td:contains(${node.hostname})`).length === 0) {
      cy.findByTitle('Create Node').click();
      cy.findByLabelText(/hostname/i).type(node.hostname);
      cy.findByLabelText(/port/i).type(String(node.port));
      cy.findByLabelText(/user/i).type(node.user);
      cy.findByLabelText(/password/i).type(node.password);
      cy.findByText('CREATE').click();
    }
    cy.findByText(node.hostname)
      .siblings('td')
      .find('input[type="checkbox"]')
      .click();
    cy.findByText('SAVE').click();
    cy.findAllByText('NEXT').filter(':visible').click();
  });
  cy.end();
});

Cypress.Commands.add(
  'createWorkspace',
  (options: CreateWorkspaceOption = {}) => {
    const {
      workspaceName,
      node = generateNodeIfNeeded(),
      closeOnFailureOrFinish = true,
    } = options;

    // Click the quick start dialog
    cy.visit('/');

    // Wait until page is loaded
    cy.wait(2000);
    cy.closeDialog();

    cy.findByTitle('Create a new workspace').click();
    cy.findByText('QUICK CREATE').should('exist').click();

    // Step1: workspace name
    if (workspaceName) {
      cy.findByLabelText(/workspace name/i)
        .clear()
        .type(workspaceName);
    }

    cy.findByTestId('setup-workspace-form').findByText('NEXT').click();

    // Step2: select nodes
    cy.contains('p:visible', 'Click here to select nodes').click();
    cy.addNode(node);

    // Step3: skip volume setup
    cy.findByTestId('setup-volume-form').findByText('NEXT').click();

    // Step4: create workspace
    cy.findByTestId('review-form').findByText('SUBMIT').click();

    cy.findByTestId('create-workspace-progress-dialog').should('be.visible');
    cy.findByTestId('stepper-close-button').should('be.visible');

    if (closeOnFailureOrFinish) {
      // if the RETRY button is enabled, the task is stopped and has not been completed
      cy.get('body').then(($body) => {
        const $retryButton = $body.find('[data-testid="stepper-retry-button"]');
        if ($retryButton.filter(':visible').length > 0) {
          // when we refresh the browser, the native alert should prompt
          // TODO: assert the alert should be appears [Tracked by https://github.com/oharastream/ohara/issues/5381]

          // when we click the CLOSE button, the ABORT confirm dialog should prompt
          cy.findByTestId('stepper-close-button').click();
          cy.findByTestId('abort-task-confirm-dialog').should('be.visible');
          cy.findByTestId('confirm-button-ABORT').should('be.visible').click();
        } else {
          cy.findByTestId('stepper-close-button').click();
        }
      });
      cy.findByTestId('create-workspace-progress-dialog').should(
        'not.be.visible',
      );
    }

    cy.end();
  },
);

Cypress.Commands.add(
  'createWorkspaceByApi',
  (options: CreateWorkspaceByApiOption = {}) => {
    const { workspaceName = 'workspace1', node } = options;

    cy.wrap(null, { log: false }).then(async () => {
      const result = await createServicesInNodes({
        withWorker: true,
        withBroker: true,
        withZookeeper: true,
        withWorkspace: true,
        useRandomName: false,
        workspaceName,
        node,
      });

      Cypress.log({
        name: 'createWorkspaceByApi',
        displayName: `Command`,
        message: `${workspaceName} created!`,
        consoleProps: () => result,
      });
    });

    // Reloads the page in order to fetch definition APIs
    cy.visit('/', { log: false });
  },
);

Cypress.Commands.add('deleteServicesByApi', () => {
  cy.wrap(null, { log: false }).then(async () => {
    const result = await deleteAllServices();
    Cypress.log({
      name: 'deleteServicesByApi',
      displayName: `Command`,
      message: `All services were deleted!`,
      consoleProps: () => result,
    });
  });
});

Cypress.Commands.add(
  'getTableCellByColumn',
  // this action should be a parent command
  { prevSubject: false },
  (
    $table: JQuery<HTMLTableElement>,
    columnName: string,
    columnValue?: string,
    rowFilter?: (row: JQuery<HTMLTableElement>) => boolean,
  ) => {
    cy.log(
      `Find column['${columnName}'] with value['${columnValue}'] from table`,
    );
    const header = $table.find('thead tr').find(`th:contains("${columnName}")`);
    const index = $table.find('thead tr th').index(header);

    let tableRows = null;
    if (rowFilter) {
      tableRows = $table
        .find('tbody tr')
        .filter((_, element) => rowFilter(Cypress.$(element)));
    } else {
      tableRows = $table.find('tbody tr');
    }

    const finalElement = columnValue
      ? tableRows.has(`td:contains("${columnValue}")`).length === 0
        ? null
        : tableRows.find(`td:contains("${columnValue}")`)
      : tableRows
          .map((_, element) => Cypress.$(element).find('td').eq(index))
          .get()
          .shift();
    return cy.wrap(finalElement);
  },
);

// Settings
Cypress.Commands.add(
  'switchSettingSection',
  (section: SettingSection, listItem?: string) => {
    cy.get('body').then(($body) => {
      // check whether we are in the homepage or not
      if (
        $body.find('div[data-testid="workspace-settings-dialog"]').length > 0
      ) {
        // force to visit the root path
        cy.visit('/');
      }
      cy.get('#navigator').within(() => {
        cy.get('button').should('have.length', 1).click();
      });
    });

    // enter the settings dialog
    cy.findAllByRole('menu').filter(':visible').find('li').click();

    if (listItem) {
      cy.contains('h2', section)
        .parent('section')
        .find('ul')
        .contains('li', listItem)
        .should('have.length', 1)
        .as('section');
    } else {
      cy.contains('h2', section)
        .parent('section')
        .find('ul')
        .should('have.length', 1)
        .as('section');
    }

    cy.get('@section')
      // We need to "offset" the element we just scrolled from the header of Settings
      // which has 64px height
      .scrollIntoView({ offset: { top: -64, left: 0 } })
      .should('be.visible')
      .click();
  },
);

Cypress.Commands.add(
  'createSharedTopic',
  (name = generate.serviceName({ prefix: 'topic' })) => {
    cy.switchSettingSection(SettingSection.topics);

    // add shared topics
    cy.findByTitle('Create Topic').should('be.enabled').click();

    cy.findVisibleDialog().within(() => {
      cy.findAllByLabelText('Topic name', { exact: false })
        .filter(':visible')
        .type(name);
      cy.findAllByLabelText('Partitions', { exact: false })
        .filter(':visible')
        .type('1');
      cy.findAllByLabelText('Replication factor', { exact: false })
        .filter(':visible')
        .type('1');
      cy.contains('button', 'CREATE').click();
    });

    cy.get('.shared-topic:visible')
      .find('table')
      .within(($table) => {
        cy.getTableCellByColumn($table, 'Name', name).should('exist');
        cy.getTableCellByColumn($table, 'State')
          .invoke('html')
          .should('equal', State.RUNNING);
      });

    cy.findByTestId('workspace-settings-dialog-close-button').click();
  },
);

Cypress.Commands.add('closeDialog', () => {
  cy.get('body', { log: false }).then(($body) => {
    const $dialogs = $body.find('[role="dialog"]');

    if ($dialogs.filter(':visible').length > 0) {
      // Using keyboard instead of a mouse click to avoid Cypress' detached DOM issue
      cy.get('body').type('{esc}');

      Cypress.log({
        name: 'closeDialog',
        displayName: `Command`,
        message: `An active dialog were closed`,
        consoleProps: () => ({ dialog: $dialogs.filter(':visible') }),
      });
    }
  });
});

Cypress.Commands.add('closeSnackbar', () => {
  cy.findByTestId('snackbar').find('button:visible').click();
});

Cypress.Commands.add('findVisibleDialog', () => {
  cy.findAllByRole('dialog').filter(':visible').should('have.length', 1);
});
