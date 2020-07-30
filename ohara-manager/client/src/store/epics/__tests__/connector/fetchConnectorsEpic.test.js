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

import { keyBy } from 'lodash';
import { throwError } from 'rxjs';
import { TestScheduler } from 'rxjs/testing';

import { LOG_LEVEL } from 'const';
import * as connectorApi from 'api/connectorApi';
import * as actions from 'store/actions';
import fetchConnectorsEpic from '../../connector/fetchConnectorsEpic';
import { ENTITY_TYPE } from 'store/schema';
import { getId } from 'utils/object';
import { entities } from 'api/__mocks__/connectorApi';

jest.mock('api/connectorApi');

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

it('fetch connectors should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a----------|';
    const expected = '--a 99ms (u|)';
    const subs = '    ^------------!';

    const action$ = hot(input, {
      a: {
        type: actions.fetchConnectors.TRIGGER,
      },
    });
    const output$ = fetchConnectorsEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.fetchConnectors.REQUEST,
      },
      u: {
        type: actions.fetchConnectors.SUCCESS,
        payload: {
          entities: {
            [ENTITY_TYPE.connectors]: keyBy(entities, (e) => getId(e)),
          },
          result: entities.map((e) => getId(e)),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('fetch connector multiple times within period should be got latest result', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a--b----------|';
    const expected = '--a--b 99ms (u|)';
    const subs = '    ^---------------!';

    const action$ = hot(input, {
      a: {
        type: actions.fetchConnectors.TRIGGER,
      },
      b: {
        type: actions.fetchConnectors.TRIGGER,
      },
    });
    const output$ = fetchConnectorsEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.fetchConnectors.REQUEST,
      },
      b: {
        type: actions.fetchConnectors.REQUEST,
      },
      u: {
        type: actions.fetchConnectors.SUCCESS,
        payload: {
          entities: {
            [ENTITY_TYPE.connectors]: keyBy(entities, (e) => getId(e)),
          },
          result: entities.map((e) => getId(e)),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('throw exception of fetch connector should also trigger event log action', () => {
  const error = {
    status: -1,
    data: {},
    title: 'mock get all connectors failed',
  };
  const spyCreate = jest
    .spyOn(connectorApi, 'getAll')
    .mockReturnValueOnce(throwError(error));

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a-----|';
    const expected = '--(aeu)-|';
    const subs = '    ^-------!';

    const action$ = hot(input, {
      a: {
        type: actions.fetchConnectors.TRIGGER,
      },
    });
    const output$ = fetchConnectorsEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.fetchConnectors.REQUEST,
      },
      e: {
        type: actions.fetchConnectors.FAILURE,
        payload: { ...error },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          ...error,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(spyCreate).toHaveBeenCalled();
  });
});
