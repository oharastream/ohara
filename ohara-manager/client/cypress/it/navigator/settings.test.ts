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
import { hashByGroupAndName } from '../../../src/utils/sha';
import { SettingSection } from '../../types';
import { NodeRequest } from '../../../src/api/apiInterface/nodeInterface';

describe('Navigator', () => {
  // generate fake node
  const node: NodeRequest = generate.node();

  // generate topics
  const t1 = generate.serviceName({ prefix: 't1' });
  const t2 = generate.serviceName({ prefix: 't2' });
  // generate setting fillings
  const setting = generate.word();

  // we use the default workspace name and group for file tags
  const workspaceKey = {
    name: 'workspace1',
    group: 'workspace',
  };
  const fileGroup = hashByGroupAndName(workspaceKey.group, workspaceKey.name);

  // create files
  const source = {
    fixturePath: 'jars',
    name: 'ohara-it-source.jar',
    group: fileGroup,
  };
  const sink = {
    fixturePath: 'jars',
    name: 'ohara-it-sink.jar',
    group: fileGroup,
  };
  const stream = {
    fixturePath: 'jars',
    name: 'ohara-it-stream.jar',
    group: fileGroup,
  };
  const fakeJar = {
    name: 'fake.jar',
    group: fileGroup,
  };
  const files = [source, sink, stream];

  before(() => {
    cy.deleteServicesByApi();
    cy.createWorkspaceByApi({ node });
  });

  beforeEach(() => {
    // our tests should begin from home page
    cy.visit('/');
  });

  context('Settings', () => {
    it('should have navigator after adding a workspace', () => {
      // show workspace in header
      cy.get('#navigator').within(() => {
        // we should have a menu button of current workspace
        cy.contains('button', /workspace1/i);
        cy.get('#pipeline').within(() => {
          // should have no pipeline exists
          cy.get('ul li').should('not.exist');
        });
        cy.get('#outline').within(() => {
          // should have no outline exists
          cy.get('ul li').should('not.exist');
        });
      });
    });

    it('should be able to click the workspace menu', () => {
      cy.get('#navigator').within(() => {
        cy.contains('button', /workspace1/i).click();
      });

      cy.findAllByRole('menu')
        .filter(':visible')
        .should('have.length', 1)
        .find('li')
        // we have only one menu, "Settings"
        .should('have.length', 1)
        .type('{ESC}');
    });

    it('should be able to see settings of current workspace', () => {
      cy.get('#navigator').within(() => {
        cy.contains('button', /workspace1/i).click();
      });

      cy.findAllByRole('menu').filter(':visible').find('li').click();

      // assert all the sections
      cy.findVisibleDialog().within(() => {
        cy.get('section').should(
          'have.length',
          Object.keys(SettingSection).length,
        );
        for (const section of Object.values(SettingSection)) {
          cy.contains('section', section);
        }
      });
    });
  });

  context('Topics Settings', () => {
    it('should have no topics when page loaded', () => {
      cy.switchSettingSection(SettingSection.topics);

      // there should not have any topic initially
      cy.get('div.shared-topic').within(() => {
        cy.contains('tr', 'No records to display');
      });
      cy.get('div.pipeline-only-topic').within(() => {
        cy.contains('tr', 'No records to display');
      });
    });

    it('should be able to add shared topics', () => {
      cy.switchSettingSection(SettingSection.topics);

      // add shared topics
      cy.findByTitle('Create Topic').should('be.enabled').click();

      cy.findVisibleDialog().within(() => {
        cy.findAllByLabelText('Topic name', { exact: false })
          .filter(':visible')
          .type(t1);
        cy.findAllByLabelText('Partitions', { exact: false })
          .filter(':visible')
          .type('1');
        cy.findAllByLabelText('Replication factor', { exact: false })
          .filter(':visible')
          .type('1');
        cy.contains('button', 'CREATE').click();
      });

      // the new added topic should exist and running
      cy.findByText(t1).parent('tr').contains('td', 'RUNNING');

      cy.findByTitle('View topic').click();
      cy.findVisibleDialog()
        .within(() => {
          cy.get('table')
            // we should only have 1 table (information) in fake mode
            .first()
            .within(($table) => {
              // assert topic name
              cy.getTableCellByColumn($table, 'Name', t1).should('exist');
              // this topic should be shared
              cy.getTableCellByColumn($table, 'Type', 'Shared').should('exist');
            });
        })
        // press "ESC" again back to topic list
        .trigger('keydown', { keyCode: 27, which: 27 });

      // delete topic
      cy.findByTitle('Delete topic').click();
      // cancel deletion
      cy.findVisibleDialog().within(() => {
        cy.contains('button', 'CANCEL').click();
      });
      cy.findByTitle('Delete topic').click();
      cy.findVisibleDialog().within(() => {
        cy.contains('button', 'DELETE').click();
      });
      // the topic should not exist
      cy.findByText(t1).should('not.exist');
    });

    it('should be able to filter and sort topics by name', () => {
      cy.switchSettingSection(SettingSection.topics);

      // add multiple topics
      cy.findByTitle('Create Topic').should('be.enabled').click();
      cy.findVisibleDialog().within(() => {
        cy.findAllByLabelText('Topic name', { exact: false })
          .filter(':visible')
          .type(t1);
        cy.findAllByLabelText('Partitions', { exact: false })
          .filter(':visible')
          .type('1');
        cy.findAllByLabelText('Replication factor', { exact: false })
          .filter(':visible')
          .type('1');
        cy.contains('button', 'CREATE').click();
      });
      cy.findByTitle('Create Topic').should('be.enabled').click();
      cy.findVisibleDialog().within(() => {
        cy.findAllByLabelText('Topic name', { exact: false })
          .filter(':visible')
          .type(t2);
        cy.findAllByLabelText('Partitions', { exact: false })
          .filter(':visible')
          .type('1');
        cy.findAllByLabelText('Replication factor', { exact: false })
          .filter(':visible')
          .type('1');
        cy.contains('button', 'CREATE').click();
      });
      // should be able to filter topics by name
      cy.get('div.shared-topic').within(() => {
        cy.findAllByPlaceholderText('Search').filter(':visible').type(t2);
        cy.findByText(t1).should('not.exist');
        cy.findByText(t2).should('exist');
        cy.findAllByPlaceholderText('Search').filter(':visible').clear();

        // sort the topics by name
        cy.get('thead tr th')
          .first()
          .find('.MuiTableSortLabel-root')
          .dblclick();

        // the t2 topic should be first element in reverse order
        cy.get('tbody tr').first().contains('td', t2);
      });

      cy.findAllByTitle('Delete topic').first().click();
      cy.findVisibleDialog().within(() => {
        cy.contains('button', 'DELETE').click();
      });

      // the t2 topic should not exist
      cy.findByText(t2).should('not.exist');

      cy.findByTitle('Delete topic').click();
      cy.findVisibleDialog().within(() => {
        cy.contains('button', 'DELETE').click();
      });

      // the t1 topic should not exist
      cy.findByText(t1).should('not.exist');

      // click back arrow button to settings page
      cy.findAllByText('Topics in this workspace')
        .filter(':visible')
        .siblings('button')
        .click();
    });

    it('validation of topic creation', () => {
      cy.switchSettingSection(SettingSection.topics);

      cy.findByTitle('Create Topic').should('be.enabled').click();
      cy.findVisibleDialog().within(() => {
        cy.findAllByLabelText('Topic name', { exact: false })
          .filter(':visible')
          .type(t1);
        // only accept positive partitions
        cy.findAllByLabelText('Partitions', { exact: false })
          .filter(':visible')
          .type('0')
          .blur();
        cy.findByText('Partitions must be greater than or equal to 1').should(
          'exist',
        );
        // only accept positive replication factor
        cy.findAllByLabelText('Replication factor', { exact: false })
          .filter(':visible')
          .type('0')
          .blur();
        cy.findByText(
          'Replication factor must be greater than or equal to 1',
        ).should('exist');
        // replication factor could not bigger than node size
        cy.findAllByLabelText('Replication factor', { exact: false })
          .filter(':visible')
          .clear()
          .type('2');
        cy.findByText(
          'Replication factor should be less than or equal to 1 broker as there is only 1 available in the current workspace',
        ).should('exist');
        cy.contains('button', 'CANCEL').click();
      });
    });
  });

  context('Autofill Settings', () => {
    it('should have no data initially', () => {
      cy.switchSettingSection(SettingSection.autofill);

      // there should not have any setting filling initially
      cy.get('div.section-page-content').within(() => {
        cy.get('ul li').should('not.exist');
      });
    });

    it('should be able to add setting fillings', () => {
      cy.switchSettingSection(SettingSection.autofill);

      cy.contains('button', 'ADD AUTOFILL').click();

      // add a setting filling
      cy.get('input[name="displayName"]').type(setting);

      cy.contains('button', 'ADD KEY').click();
      cy.get('input[name="settings[0].key"]').type('completed');
      cy.findByText('completed.folder').click();
      cy.get('input[name="settings[0].value"]').type('fake');

      cy.contains('button', 'ADD KEY').click();
      cy.get('input[name="settings[1].key"]').type('ftp.p');
      cy.findByText('ftp.port').click();
      cy.get('input[name="settings[1].value"]').type('22');

      cy.contains('button', 'ADD KEY').click();
      cy.get('input[name="settings[2].key"]').type('error');
      cy.findByText('error.folder').click();

      // remove the last one key-value pair
      cy.findAllByTitle('Delete the set of key, value')
        .filter(':visible')
        .last()
        .click();

      // click save button
      cy.contains('button', 'SAVE').click();
      cy.findByText(setting).should('exist');

      // edit this setting
      cy.findAllByTitle('Edit the autofill').filter(':visible').click();
      // could change name and add new key
      cy.get('input[name="displayName"]').clear().type('foo');
      cy.contains('button', 'ADD KEY').click();
      cy.get('input[name="settings[2].key"]').type('ftp.ho');
      cy.findByText('ftp.hostname').click();

      // click save button
      cy.contains('button', 'SAVE').click();
      // the origin setting name should not exist
      cy.findByText(setting).should('not.exist');

      // copy this setting
      cy.findAllByTitle('Copy the autofill').filter(':visible').click();
      cy.get('input[name="displayName"]').clear().type(setting);
      cy.contains('button', 'SAVE').click();

      // we have two settings now
      cy.get('div.section-page-content').within(() => {
        cy.get('ul li').should('have.length', 2);
      });

      // remove all settings
      cy.findAllByTitle('Delete the autofill')
        .filter(':visible')
        .each(($el) => {
          cy.wrap($el).click();
          cy.contains('button', 'DELETE').filter(':visible').click();
        });
    });

    it('could not add duplicate name or key of setting fillings', () => {
      cy.switchSettingSection(SettingSection.autofill);

      // add a setting filling
      cy.contains('button', 'ADD AUTOFILL').click();
      cy.get('input[name="displayName"]').type(setting);

      cy.contains('button', 'ADD KEY').click();
      cy.get('input[name="settings[0].key"]').type('completed');
      cy.findByText('completed.folder').click();
      cy.get('input[name="settings[0].value"]').type('fake');

      cy.contains('button', 'ADD KEY').click();
      cy.get('input[name="settings[1].key"]').type('completed');
      cy.findByText('completed.folder').click();
      // key could not duplicated
      cy.findByText('Key is duplicated').should('exist');

      // remove the last one key-value pair
      cy.findAllByTitle('Delete the set of key, value')
        .filter(':visible')
        .last()
        .click();

      // click save button
      cy.contains('button', 'SAVE').click();
      cy.findByText(setting).should('exist');

      // add a same name setting filling
      cy.contains('button', 'ADD AUTOFILL').click();
      cy.get('input[name="displayName"]').type(setting).blur();
      cy.findByText(
        `Name '${setting}' already existed. Please use a different name`,
      ).should('exist');

      // click cancel button
      cy.contains('button', 'CANCEL').click();

      // remove setting
      cy.findAllByTitle('Delete the autofill')
        .filter(':visible')
        .each(($el) => {
          cy.wrap($el).click();
          // click cancel deletion
          cy.contains('button', 'CANCEL').filter(':visible').click();
          // remove setting again
          cy.wrap($el).click();
          cy.contains('button', 'DELETE').filter(':visible').click();
        });
    });
  });

  context('Broker Settings', () => {
    it('should have one node in broker after creating workspace', () => {
      cy.switchSettingSection(SettingSection.broker);

      // there should have one broker node
      cy.get('div.section-page-content').within(() => {
        cy.get('tbody tr').should('have.length', 1);
        cy.findByText(node.hostname).should('be.visible');
      });
    });

    it('should be able to view node information of broker', () => {
      cy.switchSettingSection(SettingSection.broker);

      cy.findAllByTitle('View node').filter(':visible').click();
      // Ensure table data are fully loaded and ready to be tested
      cy.findByText('No records to display').should('not.exist');

      cy.findVisibleDialog().within(() => {
        cy.get('table')
          .first()
          .within(($table) => {
            // assert each field
            cy.getTableCellByColumn($table, 'Hostname', node.hostname).should(
              'exist',
            );
            cy.getTableCellByColumn(
              $table,
              'Port',
              node.port.toString(),
            ).should('exist');
            cy.getTableCellByColumn($table, 'User', node.user).should('exist');
            cy.getTableCellByColumn($table, 'Password', node.password).should(
              'exist',
            );
          });

        cy.get('table')
          .last<HTMLTableElement>()
          .within(($table) => {
            // assert broker should exist in services list
            // the name of broker should be as same as "workspace"
            cy.getTableCellByColumn($table, 'Name', 'workspace1').should(
              'exist',
            );

            // assert broker should exist in services type
            cy.getTableCellByColumn($table, 'Type', 'broker').should('exist');
          });
      });
    });
  });

  context('Worker Settings', () => {
    it('should have one node in worker after creating workspace', () => {
      cy.switchSettingSection(SettingSection.worker);

      // there should have one worker node
      cy.get('div.section-page-content').within(() => {
        cy.get('tbody tr').should('have.length', 1);
        cy.findByText(node.hostname).should('be.visible');
      });
    });

    it('should be able to view node information of worker', () => {
      cy.switchSettingSection(SettingSection.worker);

      cy.findAllByTitle('View node').filter(':visible').click();

      cy.findVisibleDialog().within(() => {
        // Ensure table data are fully loaded and ready to be tested
        cy.findByText('No records to display').should('not.exist');

        cy.get('table')
          .first()
          .within(($table) => {
            // assert each field
            cy.getTableCellByColumn($table, 'Hostname', node.hostname).should(
              'exist',
            );
            cy.getTableCellByColumn(
              $table,
              'Port',
              node.port.toString(),
            ).should('exist');
            cy.getTableCellByColumn($table, 'User', node.user).should('exist');
            cy.getTableCellByColumn($table, 'Password', node.password).should(
              'exist',
            );
          });

        cy.get('table')
          .last<HTMLTableElement>()
          .within(($table) => {
            // assert worker should exist in services list
            // the name of worker should be as same as "workspace"
            cy.getTableCellByColumn($table, 'Name', 'workspace1').should(
              'exist',
            );

            // assert worker should exist in services type
            cy.getTableCellByColumn($table, 'Type', 'worker').should('exist');
          });
      });

      cy.closeDialog();
    });
  });

  context('Nodes Settings', () => {
    it('should have one node in workspace', () => {
      cy.switchSettingSection(SettingSection.nodes);

      // there should have one node
      cy.get('div.section-page-content').within(() => {
        cy.get('tbody tr').should('have.length', 1);
        cy.get('tbody tr').contains('td', node.hostname);
        cy.get('tbody tr')
          .children('td')
          // the "Used" column
          .eq(4)
          .invoke('html')
          .should(
            'equal',
            '<div>Broker</div><div>Worker</div><div>Zookeeper</div>',
          );
      });
    });
  });

  context('Files Settings', () => {
    it('should be able to add and remove files', () => {
      cy.switchSettingSection(SettingSection.files);

      cy.get('div.section-page-content').within(() => {
        // upload the files by custom command "createJar"
        files.forEach((file) => {
          cy.get('input[type="file"]').then((element) => {
            cy.createJar(file).then((params) => {
              (element[0] as HTMLInputElement).files = params.fileList;
              cy.wrap(element).trigger('change', { force: true });
            });
          });
        });

        // after upload file, click the upload file again
        cy.wait(1000);
        cy.findByTitle('Upload File').click();

        cy.findByText(source.name).should('exist');
        cy.findByText(sink.name).should('exist');
        cy.findByText(stream.name).should('exist');

        // check the source file could be removed
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn($table, 'Name', source.name)
              .siblings('td')
              .last()
              .within((el$) => {
                el$.find('div[title="Delete file"]').click();
              });
          });
      });
      // confirm dialog
      cy.findByTestId('confirm-button-DELETE').click();

      // after removed, the file should not be existed
      cy.get('div.section-page-content').within(() => {
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn($table, 'Name', source.name).should(
              'not.exist',
            );
          });

        //filter
        cy.findAllByPlaceholderText('Search')
          .filter(':visible')
          .type(stream.name);
        cy.findByText(stream.name).should('exist');
        cy.findByText(sink.name).should('not.exist');

        // view the classNames of stream file
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn($table, 'Name', stream.name)
              .siblings('td')
              .last()
              .within((el$) => {
                el$.find('div[title="View file"]').click();
              });
          });
      });
      // assert the class detail of stream
      cy.findAllByText('DumbStream', { exact: false }).should('exist');
    });

    it('should be able to select file in stream jars', () => {
      cy.switchSettingSection(SettingSection.stream);
      cy.get('div.section-page-content').within(() => {
        cy.findByTitle('Add File').click();
      });

      // select the stream jar we just added
      cy.contains('h3', 'Select file').filter(':visible').should('exist');
      cy.findByText(stream.name).should('exist');
      cy.findVisibleDialog()
        .should('have.length', 1)
        .within(() => {
          cy.get('table')
            .should('have.length', 1)
            .within(($table) => {
              cy.getTableCellByColumn($table, 'Name', stream.name)
                .siblings('td')
                .first()
                .find('[type="checkbox"]')
                .check();
            });
          cy.findByText('SAVE').click();
        });

      // the selected jar should appear
      cy.get('div.section-page-content').within(() => {
        cy.findByText(stream.name).should('exist');
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn($table, 'Name', stream.name).should(
              'exist',
            );
            cy.getTableCellByColumn($table, 'Valid')
              .should('exist')
              // the upload jar should be valid
              .find('svg[severity="success"]')
              .should('exist');
          });
      });

      // upload a fake jar
      cy.get('div.section-page-content').within(() => {
        cy.findByTitle('Add File').click();
      });

      cy.findVisibleDialog().within(() => {
        cy.get('input[type="file"]').then((element) => {
          const file = new File([], fakeJar.name);
          const dataTransfer = new DataTransfer();
          dataTransfer.items.add(file);
          const fileList = dataTransfer.files;
          (element[0] as HTMLInputElement).files = fileList;
          cy.wrap(element).trigger('change', { force: true });
        });
      });
      // after upload file, click the upload file again
      cy.wait(1000);
      cy.findByTitle('Upload File').click();

      // select the fake jar and de-select stream jar
      cy.findVisibleDialog().within(() => {
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn($table, 'Name', stream.name)
              .siblings('td')
              .first()
              .find('[type="checkbox"]')
              .uncheck();
            cy.getTableCellByColumn($table, 'Name', fakeJar.name)
              .siblings('td')
              .first()
              .find('[type="checkbox"]')
              .check();
          });
        cy.findByText('SAVE').click();
      });

      // the selected jar should appear
      cy.get('div.section-page-content').within(() => {
        cy.findByText(fakeJar.name).should('exist');
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn($table, 'Name', fakeJar.name).should(
              'exist',
            );
            cy.getTableCellByColumn($table, 'Valid')
              .should('exist')
              // the upload fake jar should not be valid
              .find('svg[severity="warning"]')
              .should('exist');
          });
      });

      // after select the fake jar as stream
      // we could not remove it from file list
      cy.get('div.section-page-content').within(() => {
        cy.findByTitle('Add File').click();
      });
      cy.findVisibleDialog().within(() => {
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn(
              $table,
              'Used',
              undefined,
              (row) => row.has(`td:contains("${fakeJar.name}")`).length > 0,
            )
              .invoke('html')
              .should('equal', '<div>Stream</div>');

            // used jar could not be removed
            cy.getTableCellByColumn(
              $table,
              'Actions',
              undefined,
              (row) => row.has(`td:contains("${fakeJar.name}")`).length > 0,
            ).within(() => {
              cy.findByTitle('Cannot delete files that are in use').should(
                'exist',
              );
            });

            // delete stream file
            cy.getTableCellByColumn(
              $table,
              'Actions',
              undefined,
              (row) => row.has(`td:contains("${stream.name}")`).length > 0,
            ).within(() => {
              cy.findByTitle('Delete file').click();
            });
          });
      });

      cy.findByTestId('confirm-button-DELETE').click();
      // Since the file list table will be altered by redux state
      // we need to wait for a period for changes applied
      cy.wait(500);
      cy.findByText(stream.name).should('not.exist');

      // close file list and de-select fake jar as stream
      cy.findVisibleDialog().within(() => {
        cy.findByText('CANCEL').click();
      });
      cy.findByTitle('Remove file').click();
      cy.findAllByText('REMOVE').filter(':visible').click();
      cy.findByText(fakeJar.name).should('not.exist');

      // after de-select the fake jar
      // we could remove it from file list
      cy.get('div.section-page-content').within(() => {
        cy.findByTitle('Add File').click();
      });
      cy.findVisibleDialog().within(() => {
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn(
              $table,
              'Used',
              undefined,
              (row) => row.has(`td:contains("${fakeJar.name}")`).length > 0,
            )
              .invoke('html')
              .should('be.empty');

            // delete fake file
            cy.getTableCellByColumn(
              $table,
              'Actions',
              undefined,
              (row) => row.has(`td:contains("${fakeJar.name}")`).length > 0,
            ).within(() => {
              cy.findByTitle('Delete file').click();
            });
          });
      });

      cy.findByTestId('confirm-button-DELETE').click();
      // Since the file list table will be altered by redux state
      // we need to wait for a period for changes applied
      cy.wait(500);
      cy.findByText(fakeJar.name).should('not.exist');
    });

    it('should be able to select file in worker plugins', () => {
      cy.switchSettingSection(
        SettingSection.worker,
        'Worker plugins and shared jars',
      );

      cy.get('div.plugins').findByTitle('Add File').click();

      // select the sink jar
      cy.findByText(sink.name).should('exist');
      cy.findVisibleDialog().within(() => {
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn($table, 'Name', sink.name)
              .siblings('td')
              .first()
              .find('[type="checkbox"]')
              .check();
          });
        cy.findByText('SAVE').click();
      });
      cy.findByText(sink.name).should('exist');

      // add the same file to shared jars
      cy.get('div.shared-jars').findByTitle('Add File').click();
      cy.findVisibleDialog().within(() => {
        cy.get('table')
          .should('have.length', 1)
          .within(($table) => {
            cy.getTableCellByColumn($table, 'Name', sink.name)
              .siblings('td')
              .first()
              .find('[type="checkbox"]')
              .check();
          });
        cy.findByText('SAVE').click();
      });

      // we have uploaded two jars into worker
      cy.findAllByText(sink.name).should('have.length', 2);

      // undo uploaded jar into shared jars
      cy.get('div.shared-jars').findByTitle('Undo add file').click();
      cy.findAllByText(sink.name).should('have.length', 1);

      cy.get('div.section-page-header').within(() => {
        // back to Settings dialog
        cy.get('button').click();
      });

      // the worker section should have 1 change warning
      cy.contains('h2', SettingSection.worker)
        .parent('section')
        .find('ul')
        .contains('span', '1');

      // restart worker
      cy.switchSettingSection(SettingSection.dangerZone, 'Restart this worker');
      cy.findVisibleDialog().within(() => {
        cy.findByText('RESTART').click();
      });

      cy.findVisibleDialog().within(() => {
        // expand the log process
        cy.findByText('Stop worker', { exact: false });
        cy.findByText('Start worker', { exact: false });

        cy.findByText('CLOSE').parent('button').should('be.enabled').click();
      });
      // close the snackbar
      cy.findByTestId('snackbar').find('button:visible').click();

      // close the settings dialog
      cy.findByTestId('workspace-settings-dialog-close-button')
        .should('be.visible')
        .click();
    });
  });
});
