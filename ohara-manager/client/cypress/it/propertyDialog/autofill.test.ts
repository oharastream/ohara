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
import { CellAction } from '../../types';
import { SOURCE } from '../../../src/api/apiInterface/connectorInterface';

describe('Property dialog - Autofill', () => {
  before(() => {
    cy.deleteServicesByApi();
    cy.createWorkspaceByApi();
  });

  beforeEach(() => {
    cy.closeDialog();
    cy.stopAndDeleteAllPipelines();
    cy.createPipeline();

    // Create a ftp source
    const sourceName = generate.serviceName({ prefix: 'source' });
    cy.addElement({
      name: sourceName,
      kind: KIND.source,
      className: SOURCE.ftp,
    });

    // Open dialog
    cy.getCell(sourceName).trigger('mouseover');
    cy.cellAction(sourceName, CellAction.config).click();
  });

  it('should render Autofill UI', () => {
    openAutofillActions();

    // Autofill list dialog
    openSelectDialog();
    cy.findVisibleDialog().within(() => {
      // Should have the dialog title
      cy.findByText('Select Autofill').should('be.visible');
      cy.findByText('CANCEL').filter(':visible').click();
    });

    // Autofill copy dialog
    openCopyDialog();

    cy.findVisibleDialog().within(() => {
      // Should have the dialog title
      cy.findByText('Copy Autofill').should('be.visible');
      cy.findByText('CANCEL').filter(':visible').click();
    });

    cy.closeDialog();
  });

  it('should able to copy an existing setting and convert to autofill', () => {
    const autofillName = generate.word();

    openAutofillActions();

    // Copy the current settings and save it
    openCopyDialog();
    cy.findVisibleDialog().within(() => {
      cy.findByLabelText(/name/).type(autofillName);
      cy.findByText('SAVE').click();
    });

    // Check if the newly added autofill is there
    openSelectDialog();
    cy.findVisibleDialog().within(() => {
      cy.findByText(autofillName).should('exist');
      cy.findByText('CANCEL').filter(':visible').click();
    });

    // Back to property dialog
    cy.closeDialog();

    // This tests that the autofill update action is not resetting the Property dialog form
    // See: https://github.com/oharastream/ohara/issues/5796
    cy.findVisibleDialog().get('.Mui-error').should('have.length', 0);
  });
});

function openSelectDialog() {
  cy.findByTitle('Autofill').should('be.visible').click();
}

function openCopyDialog() {
  cy.findByTitle('Copy').should('be.visible').click();
}

function openAutofillActions() {
  cy.findVisibleDialog().within(() => {
    cy.findByTestId('autofill-dial')
      .find('> button')
      .should('be.visible')
      .trigger('mouseover');
  });
}
