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

import React, { useState } from 'react';
import PropTypes from 'prop-types';

import NewRowModal from './NewRowModal';
import Table from './Table';
import { DeleteDialog } from 'components/common/Mui/Dialog';
import { NewRowBtn } from './styles';

const ColumnTable = props => {
  const [isNewRowModalActive, setIsNewRowModalActive] = useState(false);
  const [isDeleteRowModalActive, setIsDeleteRowModalActive] = useState(false);
  const [rowNameToBeDeleted, setRowNameToBeDeleted] = useState('');
  const [currRow, setCurrRow] = useState(0);

  const {
    headers,
    data,
    handleUp,
    handleDown,
    dataTypes,
    handleColumnRowUp,
    handleColumnRowDown,
    parentValues,
    handleTypeChange,
    disabled,
  } = props;

  const handleNewRowModalOpen = e => {
    e.preventDefault();
    setIsNewRowModalActive(true);
  };

  const handleNewRowModalClose = () => {
    setIsNewRowModalActive(false);
  };

  const handleNewRow = values => {
    const { handleColumnChange, parentValues } = props;
    const { columnName, newColumnName, dataType: currType } = values;

    handleColumnChange({ parentValues, columnName, newColumnName, currType });
    handleNewRowModalClose();
  };

  const handleDeleteRow = () => {
    props.handleColumnRowDelete({
      currRow,
      parentValues: props.parentValues,
    });

    handleDeleteRowModalClose();
  };

  const handleDeleteRowModalOpen = (e, order) => {
    e.preventDefault();

    const row = props.data.find(d => d.order === order);
    setIsDeleteRowModalActive(true);
    setCurrRow(order);

    setRowNameToBeDeleted(row.name);
  };

  const handleDeleteRowModalClose = () => {
    setIsDeleteRowModalActive(false);
    setCurrRow(0);
  };

  return (
    <>
      <NewRowBtn
        text="New row"
        size="small"
        data-testid="new-row-btn"
        onClick={handleNewRowModalOpen}
        disabled={disabled}
      />

      <NewRowModal
        isActive={isNewRowModalActive}
        dataTypes={dataTypes}
        handleConfirmClick={handleNewRow}
        handleModalClose={handleNewRowModalClose}
      />

      <DeleteDialog
        title="Delete row?"
        content={`Are you sure you want to delete the row: ${rowNameToBeDeleted}? This action cannot be undone!`}
        open={isDeleteRowModalActive}
        handleClose={handleDeleteRowModalClose}
        handleConfirm={handleDeleteRow}
      />

      <Table
        headers={headers}
        data={data}
        dataTypes={dataTypes}
        handleUp={handleUp}
        handleDown={handleDown}
        handleTypeChange={handleTypeChange}
        handleDeleteRowModalOpen={handleDeleteRowModalOpen}
        handleColumnRowUp={handleColumnRowUp}
        handleColumnRowDown={handleColumnRowDown}
        parentValues={parentValues}
        disabled={disabled}
      />
    </>
  );
};

ColumnTable.propTypes = {
  headers: PropTypes.array.isRequired,
  data: PropTypes.arrayOf(
    PropTypes.shape({
      order: PropTypes.number.isRequired,
      dataType: PropTypes.string.isRequired,
      name: PropTypes.string.isRequired,
      newName: PropTypes.string.isRequired,
    }),
  ),
  dataTypes: PropTypes.array.isRequired,
  handleTypeChange: PropTypes.func,
  handleUp: PropTypes.func,
  handleDown: PropTypes.func,
  handleColumnChange: PropTypes.func.isRequired,
  handleColumnRowDelete: PropTypes.func.isRequired,
  handleColumnRowUp: PropTypes.func.isRequired,
  handleColumnRowDown: PropTypes.func.isRequired,
  disabled: PropTypes.bool.isRequired,
  parentValues: PropTypes.object,
};

export default ColumnTable;
