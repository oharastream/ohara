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
import { RecommendValue } from '../../../src/api/apiInterface/definitionInterface';
import { SOURCE } from '../../../src/api/apiInterface/connectorInterface';
import { KIND } from '../../../src/const';
import { CellAction } from '../../types';

type Data = {
  order: string;
  name: string;
  newName: string;
  dataType: RecommendValue;
};
describe('Property dialog - Schema table', () => {
  before(() => {
    cy.deleteServicesByApi();
    cy.createWorkspaceByApi();
  });

  let data: Data;

  beforeEach(() => {
    cy.closeDialog();
    cy.stopAndDeleteAllPipelines();
    cy.createPipeline();

    // Create a Perf source
    const sourceName = generate.serviceName({ prefix: 'source' });
    data = {
      order: '1',
      name: generate.randomString({ length: 6 }),
      newName: generate.randomString({ length: 6 }),
      dataType: RecommendValue.LONG,
    };

    cy.addElement({
      name: sourceName,
      kind: KIND.source,
      className: SOURCE.perf,
    });

    // Open dialog
    cy.getCell(sourceName).trigger('mouseover');
    cy.cellAction(sourceName, CellAction.config).click();

    cy.findByTestId('definition-table').within(() => {
      // Ensure the table is ready
      cy.findByText('Schema').should('exist');
      // Should be an empty table
      cy.findByText('No records to display').should('exist');
    });
  });

  it('should be able to add new schema', () => {
    addSchema(data);

    // Assert the data are present
    cy.findByTestId('definition-table').within(() => {
      cy.findByText('No records to display').should('not.exist');
      cy.findByText(data.order).should('exist');
      cy.findByText(data.name).should('exist');
      cy.findByText(data.newName).should('exist');
      cy.findByText(data.dataType).should('exist');
    });
  });

  it('should be able to delete a schema', () => {
    addSchema(data);

    cy.findByTestId('definition-table').within(() => {
      // Delete the data
      cy.findByTitle('Delete').click();
      cy.findByText('Are you sure you want to delete this row?');
      cy.findByTitle('Save').click();
      cy.findByText('No records to display').should('exist');
    });
  });

  it('should be able to update a schema', () => {
    const newData = {
      order: generate.number().toString(),
      name: generate.randomString({ length: 6 }),
      newName: generate.randomString({ length: 6 }),
      dataType: RecommendValue.SHORT,
    };

    addSchema(data);

    cy.findByTestId('definition-table').within(() => {
      // Assert the data are present
      cy.findByText('No records to display').should('not.exist');
      cy.findByText(data.order).should('exist');
      cy.findByText(data.name).should('exist');
      cy.findByText(data.newName).should('exist');
      cy.findByText(data.dataType).should('exist');

      // Edit
      cy.findByTitle('Edit').click();

      cy.findByPlaceholderText('1').clear().type(newData.order);
      cy.findByPlaceholderText('name').clear().type(newData.name);
      cy.findByPlaceholderText('newName').clear().type(newData.newName);
      cy.get('div[aria-label="dataType"]').click();
    });

    cy.get('.MuiMenu-paper:visible').findByText(newData.dataType).click();

    cy.findByTestId('definition-table').within(() => {
      cy.findByTitle('Save').click();

      cy.findByText(newData.order).should('exist');
      cy.findByText(newData.name).should('exist');
      cy.findByText(newData.newName).should('exist');
      cy.findByText(newData.dataType).should('exist');
    });
  });

  it('should prevent an empty schema row from creating', () => {
    // Click Add button
    cy.findByTestId('definition-table').within(() => {
      cy.findByTitle('Add').should('exist').click();
    });

    // Try to save a schema row without fill out the row
    cy.findByTestId('definition-table').within(() => {
      cy.findByTitle('Save').click();
    });

    // Assert the snackbar message
    cy.findByText('All fields are required').should('exist').click();
  });

  it('should display invalid message while users are adding a new schema row', () => {
    // Click Add button
    cy.findByTestId('definition-table').within(() => {
      cy.findByTitle('Add').should('exist').click();
    });

    // 👉 Order
    // Zero and negative numbers are invalid
    cy.findByPlaceholderText('1').type('0');
    cy.findByText('Order starts from 1').should('exist');
    cy.findByPlaceholderText('1').type('-1');
    cy.findByText('Order starts from 1').should('exist');

    // Use a valid number to pass the check
    cy.findByPlaceholderText('1').clear().type('2');
    cy.findByText('Order starts from 1').should('not.exist');

    // 👉 Name
    const name = generate.randomString({ length: 4 });
    // We need to first type a name and then clear it since this is the behavior from Material Table
    cy.findByPlaceholderText('name').type(name).clear();
    // It's required
    cy.findByText('Field is required');

    // Enter a new name in order to pass the check
    cy.findByPlaceholderText('name').clear().type(name);
    cy.findByText('Field is required').should('not.exist');

    // 👉 New name
    const newName = generate.randomString({ length: 4 });
    // We need to first type a name and then clear it since this is the behavior from Material Table
    cy.findByPlaceholderText('newName').type(newName).clear();

    // It's required
    cy.findByText('Field is required');

    // Enter a new name in order to pass the check
    cy.findByPlaceholderText('newName').clear().type(newName);
    cy.findByText('Field is required').should('not.exist');
  });

  it('should prevent users from picking up an order that is already taken', () => {
    addSchema(data);

    cy.findByTestId('definition-table').within(() => {
      cy.findByText(data.order).should('exist');
    });

    const newOrder = '2';

    cy.findByTestId('definition-table').within(() => {
      cy.findByTitle('Add').should('exist').click();

      // Order `1` is already taken
      cy.findByPlaceholderText('1').clear().type(data.order);
      cy.findByText('Order is taken').should('exist');

      // Use a different order should pass the check
      cy.findByPlaceholderText('1').clear().type(newOrder);
      cy.findByText('Order is taken').should('not.exist');
    });
  });
});

function addSchema(data: Data) {
  cy.findByTestId('definition-table').within(() => {
    // Add a new schema
    cy.findByTitle('Add').should('exist').click();

    cy.findByPlaceholderText('1').type(data.order);
    cy.findByPlaceholderText('name').type(data.name);
    cy.findByPlaceholderText('newName').type(data.newName);
    cy.get('div[aria-label="dataType"]').click();
  });

  // Save it
  cy.get('.MuiMenu-paper:visible').findByText(data.dataType).click();
  cy.findByTestId('definition-table').within(() => {
    cy.findByTitle('Save').click();
  });
}
