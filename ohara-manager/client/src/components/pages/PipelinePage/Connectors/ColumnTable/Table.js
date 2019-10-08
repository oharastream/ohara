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

import React from 'react';
import PropTypes from 'prop-types';
import { isNull } from 'lodash';

import { LinkButton } from 'components/common/Form';
import { StyledTable } from './styles';

const Table = props => {
  const {
    headers,
    data,
    dataTypes,
    handleTypeChange,
    handleDeleteRowModalOpen,
    handleColumnRowUp,
    handleColumnRowDown,
    parentValues,
    disabled,
  } = props;

  const _data = isNull(data) ? [] : data;

  return (
    <StyledTable>
      <thead>
        <tr>
          {headers.map(header => (
            <th key={header}>{header}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {_data.map(({ order, name, newName, dataType: currType }) => {
          return (
            <tr key={order}>
              <td>{order}</td>
              <td>
                <select
                  disabled={disabled}
                  onChange={e => handleTypeChange(e, order)}
                  value={currType}
                >
                  {dataTypes.map(dataType => {
                    return <option key={dataType}>{dataType}</option>;
                  })}
                </select>
              </td>
              <td>{name}</td>
              <td>{newName}</td>
              <td>
                <LinkButton
                  disabled={disabled}
                  handleClick={e =>
                    handleColumnRowUp(e, { order, parentValues })
                  }
                >
                  <i className="fas fa-arrow-up" />
                </LinkButton>
              </td>
              <td>
                <LinkButton
                  disabled={disabled}
                  handleClick={e =>
                    handleColumnRowDown(e, { order, parentValues })
                  }
                >
                  <i className="fas fa-arrow-down" />
                </LinkButton>
              </td>
              <td>
                <LinkButton
                  disabled={disabled}
                  handleClick={e => handleDeleteRowModalOpen(e, order)}
                >
                  <i className="far fa-trash-alt" />
                </LinkButton>
              </td>
            </tr>
          );
        })}
      </tbody>
    </StyledTable>
  );
};

Table.propTypes = {
  headers: PropTypes.arrayOf(PropTypes.string),
  data: PropTypes.arrayOf(
    PropTypes.shape({
      order: PropTypes.number.isRequired,
      dataType: PropTypes.string.isRequired,
      name: PropTypes.string.isRequired,
      newName: PropTypes.string.isRequired,
    }),
  ),
  dataTypes: PropTypes.array,
  handleTypeChange: PropTypes.func,
  handleColumnRowUp: PropTypes.func.isRequired,
  handleColumnRowDown: PropTypes.func.isRequired,
  handleDeleteRowModalOpen: PropTypes.func.isRequired,
  disabled: PropTypes.bool.isRequired,
  parentValues: PropTypes.object,
};

export default Table;
