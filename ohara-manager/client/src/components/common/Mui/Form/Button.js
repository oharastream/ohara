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
import MuiButton from '@material-ui/core/Button';

const Button = props => {
  const {
    text,
    className,
    component,
    color = 'primary',
    variant = 'contained',
    size = 'medium',
    onClick,
    testId,
    disabled = false,
  } = props;

  return (
    <MuiButton
      component={component}
      className={className}
      color={color}
      variant={variant}
      onClick={onClick}
      size={size}
      data-testid={testId}
      disabled={disabled}
    >
      {text}
    </MuiButton>
  );
};

Button.propTypes = {
  text: PropTypes.string.isRequired,
  size: PropTypes.string,
  className: PropTypes.string,
  color: PropTypes.string,
  variant: PropTypes.string,
  onClick: PropTypes.func,
  component: PropTypes.string,
  testId: PropTypes.string,
  disabled: PropTypes.bool,
};

export default Button;
