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

import { noop, throwError } from 'rxjs';
import { TestScheduler } from 'rxjs/testing';

import * as connectorApi from 'api/connectorApi';
import createConnectorEpic from '../../connector/createConnectorEpic';
import * as actions from 'store/actions';
import { getId } from 'utils/object';
import { entity as connectorEntity } from 'api/__mocks__/connectorApi';
import { LOG_LEVEL } from 'const';

jest.mock('api/connectorApi');
const mockedPaperApi = jest.fn(() => {
  return {
    updateElement: () => noop(),
    removeElement: () => noop(),
  };
});
const paperApi = new mockedPaperApi();

const connectorId = getId(connectorEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

it('create connector should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a         ';
    const expected = '--a 99ms u';
    const subs = '    ^-----------';

    const action$ = hot(input, {
      a: {
        type: actions.createConnector.TRIGGER,
        payload: {
          values: connectorEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createConnectorEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createConnector.REQUEST,
        payload: {
          connectorId,
        },
      },
      u: {
        type: actions.createConnector.SUCCESS,
        payload: {
          connectorId,
          entities: {
            connectors: {
              [connectorId]: connectorEntity,
            },
          },
          result: connectorId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('create multiple connectors should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-ab          ';
    const expected = '--ab 98ms uv';
    const subs = '    ^-------------';
    const anotherConnectorEntity = {
      ...connectorEntity,
      name: 'anotherconnector',
    };

    const action$ = hot(input, {
      a: {
        type: actions.createConnector.TRIGGER,
        payload: {
          values: connectorEntity,
          options: { paperApi },
        },
      },
      b: {
        type: actions.createConnector.TRIGGER,
        payload: {
          values: anotherConnectorEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createConnectorEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createConnector.REQUEST,
        payload: {
          connectorId,
        },
      },
      u: {
        type: actions.createConnector.SUCCESS,
        payload: {
          connectorId,
          entities: {
            connectors: {
              [connectorId]: connectorEntity,
            },
          },
          result: connectorId,
        },
      },
      b: {
        type: actions.createConnector.REQUEST,
        payload: {
          connectorId: getId(anotherConnectorEntity),
        },
      },
      v: {
        type: actions.createConnector.SUCCESS,
        payload: {
          connectorId: getId(anotherConnectorEntity),
          entities: {
            connectors: {
              [getId(anotherConnectorEntity)]: anotherConnectorEntity,
            },
          },
          result: getId(anotherConnectorEntity),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('create same connector within period should be created once only', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-aa 10s a    ';
    const expected = '--a 99ms u--';
    const subs = '    ^-------------';

    const action$ = hot(input, {
      a: {
        type: actions.createConnector.TRIGGER,
        payload: {
          values: connectorEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createConnectorEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createConnector.REQUEST,
        payload: {
          connectorId,
        },
      },
      u: {
        type: actions.createConnector.SUCCESS,
        payload: {
          connectorId,
          entities: {
            connectors: {
              [connectorId]: connectorEntity,
            },
          },
          result: connectorId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('throw exception of create connector should also trigger event log action', () => {
  const error = {
    status: -1,
    data: {},
    title: 'mock create connector failed',
  };
  const spyCreate = jest
    .spyOn(connectorApi, 'create')
    .mockReturnValueOnce(throwError(error));

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a-----|';
    const expected = '--(aeu)-|';
    const subs = '    ^-------!';

    const action$ = hot(input, {
      a: {
        type: actions.createConnector.TRIGGER,
        payload: {
          values: connectorEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createConnectorEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createConnector.REQUEST,
        payload: { connectorId },
      },
      e: {
        type: actions.createConnector.FAILURE,
        payload: { ...error, connectorId },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          ...error,
          connectorId,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(spyCreate).toHaveBeenCalled();
  });
});
