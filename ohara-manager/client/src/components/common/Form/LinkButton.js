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
import styled from 'styled-components';

const ButtonWrapper = styled.button`
  border: none;
  color: ${props => (props.disabled ? props.theme.gray : props.theme.blue)};
  background-color: transparent;

  &:hover {
    color: ${props =>
      props.disabled ? props.theme.gray : props.theme.blueHover};
    cursor: ${props => (props.disabled ? 'default' : 'pointer')};
  }
`;

const LinkButton = ({ handleClick, children, disabled = false }) => {
  return (
    <ButtonWrapper disabled={disabled} onClick={handleClick}>
      {children}
    </ButtonWrapper>
  );
};

LinkButton.propTypes = {
  disabled: PropTypes.bool,
  children: PropTypes.any,
  handleClick: PropTypes.func,
};

export default LinkButton;
