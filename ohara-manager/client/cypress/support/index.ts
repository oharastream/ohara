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

import '@cypress/code-coverage/support';
import _ from 'lodash';

import './defaultCommands';
import './customCommands';
import './pipelineCommands';
import { deleteAllServices } from '../utils';

after(async () => {
  // after each spec (file) tests finished, we make sure all service are stopped and deleted directly
  // even some tests of spec were failed, this command still run instantly.
  // see https://github.com/cypress-io/cypress/issues/203#issuecomment-251009808
  await deleteAllServices().then((res) => {
    // after each spec tests, we create own custom message here
    // which will print out to our browsers console
    // to indicate which objects are deleted
    Cypress.log({
      name: 'deleteAllServices',
      displayName: `Command`,
      message: 'All services were deleted',
      consoleProps: () =>
        _(res)
          .mapKeys((_value, key) => `Deleted ${key.replace('res', 's')}`)
          .mapValues((value) => value.data)
          .value(),
    }).end();
  });
});

const customAssertions = (chai: Chai.ChaiStatic, utils: Chai.ChaiUtils) => {
  const customMethodA = (_super: Chai.AssertionStatic) => {
    return function a() {
      utils.flag(this, 'message', '[Type Assert]');
      /* eslint-disable prefer-rest-params */
      // @ts-ignore
      _super.apply(this, arguments);
      /* eslint-enable prefer-rest-params */
    };
  };

  const customChainingA = () => {
    return function chainA() {
      utils.flag(this, 'object');
    };
  };

  chai.Assertion.overwriteChainableMethod(
    'a',
    (_super) => customMethodA(_super),
    () => customChainingA(),
  );
  chai.Assertion.overwriteChainableMethod(
    'an',
    (_super) => customMethodA(_super),
    () => customChainingA(),
  );
};

chai.use(customAssertions);
