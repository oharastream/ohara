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

/// <reference types="cypress" />

declare namespace Cypress {
  import {
    CreateWorkspaceOption,
    CreateWorkspaceByApiOption,
  } from './support/customCommands';

  interface FixtureRequest {
    fixturePath: string;
    name: string;
    group: string;
    tags?: Record<string, unknown>;
  }

  interface FixtureResponse {
    name: string;
    fileList: FileList;
    file: File;
    group: string;
    tags?: Record<string, unknown>;
  }

  export interface Chainable {
    createJar: (file: FixtureRequest) => Promise<FixtureResponse>;
    createNode: (node?: NodeRequest) => Chainable<NodeRequest>;
    createNodeIfNotExists: (node: NodeRequest) => Chainable<NodeResponse>;
    createWorkspace: (options?: CreateWorkspaceOption) => Chainable<null>;
    /**
     * Create a workspace by APIs, this is general fast then creating with UI (createWorkspace),
     * you should opt to use this whenever possible. But a few things you should know before using this:
     * 1. A cy.visit() is follow after all services are created with this command due or UI implementation
     * 2. The snackbar message will not show up since it's created by APIs
     * 3. This command basically utilize the `createServicesInNodes` function in the `utils.ts`
     * @param options
     * @param options.workspaceName The name of the workspace
     * @param options.node Node used in the workspace
     * @example cy.createWorkspaceByApi(); // Use default workspace name (which is `workspace1`) and default node (a random fake node)
     * @example cy.deleteNode('myawesomeworkspace'); // Specify a workspace name
     */
    createWorkspaceByApi: (
      options?: CreateWorkspaceByApiOption,
    ) => Chainable<null>;

    /**
     * Delete all services. This command uses `deleteAllServices` under the hook
     * @example cy.deleteServicesByApi();
     */
    deleteServicesByApi: () => Chainable<null>;
    /**
     * Get the _&lt;td /&gt;_ elements by required parameters.
     * <p> This function has the following combination:
     * @param {string} columnName the filtered header of table cell
     * @param {string} columnValue the filtered value of table cell
     * @param {Function} rowFilter given a function to filter the result of elements
     * @example `columnName`: filter all _&lt;td /&gt;_ elements of specific column.
     * @example `columnName + columnValue`: filter the _&lt;td /&gt;_ element of specific column and value.
     * @example `columnName + rowFilter`: filter the _&lt;td /&gt;_ element of specific column in specific rows.
     * @example `columnName + columnValue + rowFilter`: filter the _&lt;td /&gt;_ element of specific column in specific rows.
     */
    getTableCellByColumn: (
      $table: JQuery<HTMLTableElement>,
      columnName: string,
      columnValue?: string,
      rowFilter?: (row: JQuery<HTMLTableElement>) => boolean,
    ) => Chainable<JQuery<HTMLElement | HTMLElement[]>>;
    dragAndDrop: (
      shiftX: number,
      shiftY: number,
    ) => Chainable<JQuery<HTMLElement>>;
    addNode: (node?: NodeRequest) => Chainable<null>;

    /**
     * Delete a node from node list, don't confuse this with add/remove note from workspaces
     * @param {string} hostname node hostname
     * @param {boolean} isInsideNodeList whether to skip the "open node list dialog" step or not, defaults to `false`
     * @example cy.deleteNode('host123', true); // Assuming you're in the node list dialog, the open dialog step is skipped
     * @example cy.deleteNode('host123', false);
     */
    deleteNode: (hostname: string, isInsideNodeList?) => Chainable<null>;

    /**
     * Delete all nodes, note that you need to make sure these nodes are not used by any workspaces or services
     * @example cy.deleteNodesByApi();
     */
    deleteNodesByApi: () => Chainable<null>;
    addElement: (element: ElementParameters) => Chainable<null>;
    addElements: (elements: ElementParameters[]) => Chainable<null>;
    removeElement: (name: string) => Chainable<null>;

    /**
     * Get a Paper element by name
     * @param {string} name Element name
     * @param {boolean} isTopic If element is a topic
     * @example cy.getElement('mySource').should('have.text', 'running');
     * @example cy.getElement('myTopic').should('have.class', 'running');
     */
    getElementStatus: (
      name: string,
      isTopic?: boolean,
    ) => Chainable<JQuery<HTMLElement>>;
    getCell: (name: string) => Chainable<HTMLElement>;
    cellAction: (name: string, action: CELL_ACTION) => Chainable<HTMLElement>;

    /**
     * Create a connection between elements
     * @param {string[]} elements Element list, the connection will be created following the list order
     * @param {boolean} waitForApiCall If the command should wait for pipeline update API call to finish or not
     * @example cy.createConnection(['ftpSource', 'topic1', 'consoleSink']); // create a connection of ftpSource -> topic1 -> consoleSink
     */

    createConnections: (
      elements: string[],
      waitForApiCall?: boolean,
    ) => Chainable<null>;
    uploadStreamJar: () => Chainable<null>;
    // Pipeline
    createPipeline: (name?: string) => Chainable<null>;
    startPipeline: (name: string) => Chainable<null>;
    stopPipeline: (name: string) => Chainable<null>;
    deletePipeline: (name: string) => Chainable<null>;
    stopAndDeleteAllPipelines: () => Chainable<null>;

    // Settings
    switchSettingSection: (
      section: SettingSection,
      listItem?: string,
    ) => Chainable<null>;
    createSharedTopic: (name?: string) => Chainable<null>;
    closeIntroDialog: () => Chainable<null>;

    /**
     * Use `cy.findAllByRole()` to get all dialogs and filter out the invisible ones
     * @example cy.findVisibleDialog().findByText('DELETE').click();
     * @example cy.findVisibleDialog().within(() => cy.findByText('Delete').click());
     */
    findVisibleDialog: () => Chainable<HTMLElement>;

    /**
     * Close active dialog
     * @example cy.closeDialog();
     */
    closeDialog: () => Chainable<null>;

    /**
     * Close active snackbar
     * @example cy.closeSnackbar()
     */
    closeSnackbar: () => Chainable<null>;
  }
}
