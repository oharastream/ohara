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

import { get } from 'lodash';

const nameValidate = params => {
  const { values, keys = [] } = params;
  let errors = {};
  keys.forEach(key => {
    if (!get(values, key)) {
      errors[key] = 'Required field';
    } else if (get(values, key).match(/[^0-9a-z]/g)) {
      errors[key] = 'You only can use lower case letters and numbers';
    } else if (get(values, key).length > 20) {
      errors[key] = 'Must be between 1 and 20 characters long';
    }
  });
  return errors;
};

const numberValidate = params => {
  const { values, keys = [] } = params;
  let errors = {};
  keys.forEach(key => {
    if (get(values, key) <= 0) {
      errors[key] = 'Only digits larger than zero can be entered';
    }
  });
  return errors;
};

export { nameValidate, numberValidate };
