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

describe('Create Workspace', () => {
  // generate node
  const node: NodeRequest = generate.node();

  before(() => cy.deleteServicesByApi());

  beforeEach(() => {
    cy.server();
    // our tests should begin from home page
    cy.visit('/');
  });

  context('Quick Create Workspace', () => {
    it('should remember the state when close the dialog', () => {
      const workspaceName = generate.serviceName({ prefix: 'ws' });
      const nodeHost = generate.serviceName({ prefix: 'node' });
      // first visit will popup the quick create dialog
      cy.findByText('QUICK CREATE').click();

      // Workspace name
      cy.findByDisplayValue('workspace', { exact: false })
        .clear()
        .type(workspaceName);
      cy.findByTestId('setup-workspace-form').findByText('NEXT').click();

      // Node
      cy.contains('p:visible', 'Click here to select nodes').click();
      cy.findByTitle('Create Node').click();
      cy.findByLabelText(/hostname/i).type(nodeHost);
      cy.findByLabelText(/port/i).type(generate.port().toString());
      cy.findByLabelText(/user/i).type(generate.userName());
      cy.findByLabelText(/password/i).type(generate.password());
      cy.findByText('CREATE').click();
      cy.findByText(nodeHost)
        .siblings('td')
        .find('input[type="checkbox"]')
        .click();
      cy.findByText('SAVE').click();
      cy.findByTestId('setup-nodes-form').findByText('NEXT').click();

      // Volume
      cy.findByTestId('setup-volume-form').findByText('BACK').click();
      cy.contains('h6', 'Hostname')
        .siblings('div')
        .invoke('html')
        .should('equal', nodeHost);

      // Close the dialog
      cy.findByTestId('fullscreen-dialog-close-button').click();

      // back to create workspace dialog again
      // the state should keep in "select nodes"
      cy.findByText('QUICK CREATE').click();
      cy.contains('h6', 'Hostname')
        .siblings('div')
        .invoke('html')
        .should('equal', nodeHost);

      cy.deleteNodesByApi();
    });

    it('should be able to select and filter nodes', () => {
      cy.visit('/');
      cy.findByTestId('close-intro-button').click();
      cy.findByTitle('Node list').should('exist').click();

      const hostname1 = generate.serviceName();
      cy.findByTitle('Create Node').click();
      cy.findByLabelText(/hostname/i).type(hostname1);
      cy.findByLabelText(/port/i).type(generate.port().toString());
      cy.findByLabelText(/user/i).type(generate.userName());
      cy.findByLabelText(/password/i).type(generate.password());

      cy.findByText('CREATE').click();
      cy.findByText(hostname1).should('exist');

      cy.visit('/');
      cy.findByTestId('close-intro-button').click();
      cy.findByTitle('Node list').should('exist').click();

      const hostname2 = generate.serviceName();
      cy.findByTitle('Create Node').click();
      cy.findByLabelText(/hostname/i).type(hostname2);
      cy.findByLabelText(/port/i).type(generate.port().toString());
      cy.findByLabelText(/user/i).type(generate.userName());
      cy.findByLabelText(/password/i).type(generate.password());
      cy.findByText('CREATE').click();
      cy.findByText(hostname2).should('exist');

      cy.visit('/');
      cy.findByTestId('close-intro-button').click();
      cy.findByTitle('Node list').should('exist').click();

      const hostname3 = `${hostname1}${generate.serviceName()}`;
      cy.findByTitle('Create Node').click();
      cy.findByLabelText(/hostname/i).type(hostname3);
      cy.findByLabelText(/port/i).type(generate.port().toString());
      cy.findByLabelText(/user/i).type(generate.userName());
      cy.findByLabelText(/password/i).type(generate.password());
      cy.findByText('CREATE').click();
      cy.findByText(hostname3).should('exist');

      cy.visit('/');
      cy.findByText(/^quick create$/i)
        .should('exist')
        .click();

      // Step1: workspace name (using default)
      cy.findAllByText('NEXT').filter(':visible').click();

      // Since Unavailable node could not be selected
      // We check the existence only
      cy.findByText('Click here to select nodes').click();
      cy.findByText(hostname1).should('exist');
      cy.findByText(hostname2).should('exist');
      cy.findByText(hostname3).should('exist');

      // filter by hostname
      cy.findAllByPlaceholderText('Search').filter(':visible').type(hostname2);
      cy.findByText(hostname1).should('not.exist');
      cy.findByText(hostname3).should('not.exist');

      cy.deleteNodesByApi();
    });

    it('should reset the form after successfully create a workspace', () => {
      const workspaceName = generate.serviceName({ prefix: 'ws' });
      cy.createWorkspaceByApi({ workspaceName });

      // after creation with specific workspace name, the workspace should use default name
      cy.findByTitle('Create a new workspace').click();
      cy.findByText('QUICK CREATE').should('exist').click();
      cy.findByDisplayValue('workspace', { exact: false })
        .invoke('val')
        .should('equal', 'workspace1');

      cy.findAllByText('NEXT').filter(':visible').click();

      // the node selected cards should be initialized (only the "select nodes" card exists)
      cy.get('div.MuiGrid-container').children('div').should('have.length', 1);
    });

    it('should close the progress dialog automatically when "Close after finish" option is checked', () => {
      const workspaceName = generate.serviceName({ prefix: 'ws' });
      cy.visit('/');

      // Wait until the page is loaded
      cy.wait(1000);

      cy.closeDialog();

      // Create a new workspace
      cy.findByTitle('Create a new workspace').click();
      cy.findByText('QUICK CREATE').should('exist').click();

      // Step1: workspace name
      if (workspaceName) {
        // type the workspaceName by parameter
        cy.findByDisplayValue('workspace', { exact: false })
          .clear()
          .type(workspaceName);
      }
      cy.findAllByText('NEXT').filter(':visible').click();

      // Step2: select nodes
      cy.contains('p:visible', 'Click here to select nodes').click();
      cy.addNode(node);

      // Step3: set volume
      cy.findAllByText('NEXT').eq(1).filter(':visible').click();

      // Submit the form
      cy.findAllByText('SUBMIT').filter(':visible').click();

      // The progress dialog should exist
      cy.findByTestId('create-workspace-progress-dialog').should('be.visible');

      // Check the option
      cy.findByText('Close after finish').click();

      // It should be closed when done
      cy.findByTestId('create-workspace-progress-dialog').should(
        'not.be.visible',
      );
    });
  });

  context('Volume', () => {
    // FIXME: This test is currently failing due to Backend API issue
    // Re-enable this after Backend issue is resolved. See #5798
    it.skip('should able to add a volume', () => {
      const workspaceName = generate.serviceName({ prefix: 'ws' });
      const volumePath = '/home/ohara/workspace1';
      cy.visit('/');

      // Wait until the page is loaded
      cy.wait(1000);

      cy.closeDialog();

      // Create a new workspace
      cy.findByTitle('Create a new workspace').click();
      cy.findByText('QUICK CREATE').should('exist').click();

      // Step1: workspace name
      if (workspaceName) {
        // type the workspaceName by parameter
        cy.findByDisplayValue('workspace', { exact: false })
          .clear()
          .type(workspaceName);
      }
      cy.findAllByText('NEXT').filter(':visible').click();

      // Step2: select nodes
      cy.contains('p:visible', 'Click here to select nodes').click();
      cy.addNode(node);

      // Step3: volume
      // It should be disabled by default
      cy.findByLabelText(/volume path/i).should('be.disabled');

      // Add a new volume
      cy.findAllByText('Enable volumes').click();
      cy.findByLabelText(/volume path/i)
        .should('be.enabled')
        .type(volumePath)
        .blur();
      cy.findAllByText('NEXT').filter(':visible').click();

      // Submit the form
      cy.findAllByText('SUBMIT').filter(':visible').click();

      // The progress dialog should exist
      cy.findByTestId('create-workspace-progress-dialog').should('be.visible');

      // Check the option
      cy.findByText('Close after finish').click();

      // It should be closed when done
      cy.findByTestId('create-workspace-progress-dialog').should(
        'not.be.visible',
      );
    });
  });

  context('When creating a workspace failed', () => {
    it('should be able to cancel ', () => {
      const workspaceName = generate.serviceName({ prefix: 'wk' });

      // mock the API to create a worker, return 500 error
      cy.route({
        method: 'POST',
        url: 'api/workers',
        status: 500,
        response: {
          code: 'DataCheckException',
          message: 'node does not exist',
          stack: 'mock stack',
        },
      });

      cy.createNodeIfNotExists(node);
      cy.createWorkspace({
        workspaceName,
        node,
        closeOnFailureOrFinish: false,
      });

      // when an error occurs, the CANCEL button should allow clicking
      cy.findByText('CANCEL').click();
      // when the cancellation is completed, the CLOSE button should allow clicking
      cy.findByText('CLOSE').click();

      cy.closeDialog();

      // the workspace just canceled should not exist
      cy.reload();
      cy.visit(`/${workspaceName}`)
        .location()
        .should('not.eq', `/${workspaceName}`);
    });

    it('should have event logs', () => {
      const workspaceName = generate.serviceName({ prefix: 'wk' });

      // mock the API to start a worker, return 400 error
      cy.route({
        method: 'PUT',
        url: 'api/workers/*/start**',
        status: 400,
        response: {
          code: 'java.lang.IllegalArgumentException',
          message: `Does not have image:oharastream/connect-worker`,
          stack: `mock stack`,
        },
      });

      cy.createNodeIfNotExists(node);
      cy.createWorkspace({
        workspaceName,
        node,
      });
      cy.closeSnackbar();
      cy.closeDialog();

      cy.findByTitle('Event logs').click();
      cy.findByTestId('event-log-list').within(() => {
        cy.findAllByText(
          `Start workers "${workspaceName}" failed. --> Does not have image:oharastream/connect-worker`,
        ).should('exist');
        cy.findAllByText(`Failed to create workspace ${workspaceName}.`).should(
          'exist',
        );
      });
    });

    it('should display an indicator for Unstable workspace', () => {
      const workspaceName = generate.serviceName({ prefix: 'wk' });

      // mock the API to create a brokers, return 500 error
      cy.route({
        method: 'POST',
        url: 'api/brokers',
        status: 500,
        response: {
          code: 'mock code',
          message: 'mock message',
          stack: 'mock stack',
        },
      });

      cy.createNodeIfNotExists(node);
      cy.createWorkspace({
        workspaceName,
        node,
      });

      cy.closeDialog();

      // should highlight the unstable workspaces
      cy.get('#app-bar')
        .find(`div.workspace-list > span[title="${workspaceName}"]`)
        .within(() => {
          cy.findByTitle('Unstable workspace').should('exist');
        });
    });

    it('should not be able to use unstable workspace', () => {
      const workspaceName = generate.serviceName({ prefix: 'wk' });

      // Mock create zookeeper API so it always returns an error
      cy.route({
        method: 'POST',
        url: 'api/zookeepers',
        status: 500,
        response: {
          code: 'mock code',
          message: 'mock message',
          stack: 'mock stack',
        },
      });

      cy.createWorkspace({ workspaceName });
      cy.closeSnackbar();
      cy.closeDialog();

      // clicking the button of unstable workspace should show a snackbar
      cy.get('#app-bar')
        .find(`div.workspace-list > span[title="${workspaceName}"]`)
        .click();

      cy.findByTestId('snackbar')
        .contains(`This is an unstable workspace: ${workspaceName}`)
        .should('exist');

      // Cannot use browser's address bar to access the unstable workspace
      cy.visit(`/${workspaceName}`)
        .location()
        .should('not.eq', `/${workspaceName}`);

      // open the workspace list dialog
      cy.findByTestId('workspace-list-button').click();

      // buttons of unstable workspaces should be disabled
      cy.findByTestId('workspace-list-dialog')
        .should('exist')
        .contains('div.MuiCard-root', workspaceName)
        .contains('UNSTABLE WORKSPACE')
        .should('be.disabled');
    });
  });
});
