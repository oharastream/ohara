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

import { omit } from 'lodash';
import { TestScheduler } from 'rxjs/testing';
import { of } from 'rxjs';
import { delay } from 'rxjs/operators';

import startBrokerEpic from '../../broker/startBrokerEpic';
import * as brokerApi from 'api/brokerApi';
import * as actions from 'store/actions';
import { getId } from 'utils/object';
import { SERVICE_STATE } from 'api/apiInterface/clusterInterface';
import { entity as brokerEntity } from 'api/__mocks__/brokerApi';
import { LOG_LEVEL } from 'const';

jest.mock('api/brokerApi');

const bkId = getId(brokerEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

beforeEach(() => {
  // ensure the mock data is as expected before each test
  jest.restoreAllMocks();
});

it('start broker should be worked correctly', () => {
  const mockResolve = jest.fn();
  const mockReject = jest.fn();

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a        ';
    const expected = '--a 199ms v';
    const subs = ['   ^----------', '--^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.startBroker.TRIGGER,
        payload: {
          values: brokerEntity,
          resolve: mockResolve,
          reject: mockReject,
        },
      },
    });
    const output$ = startBrokerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.startBroker.REQUEST,
        payload: {
          brokerId: bkId,
        },
      },
      v: {
        type: actions.startBroker.SUCCESS,
        payload: {
          brokerId: bkId,
          entities: {
            brokers: {
              [bkId]: {
                ...brokerEntity,
                state: SERVICE_STATE.RUNNING,
              },
            },
          },
          result: bkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(mockResolve).toHaveBeenCalled();
    expect(mockResolve).toHaveBeenCalledWith({
      ...brokerEntity,
      state: 'RUNNING',
    });
    expect(mockReject).not.toHaveBeenCalled();
  });
});

it('start broker failed after reach retry limit', () => {
  // mock a 20 times "failed started" result
  const spyGet = jest.spyOn(brokerApi, 'get');
  for (let i = 0; i < 20; i++) {
    spyGet.mockReturnValueOnce(
      of({
        status: 200,
        title: 'retry mock get data',
        data: { ...omit(brokerEntity, 'state') },
      }).pipe(delay(100)),
    );
  }
  // get result finally
  spyGet.mockReturnValueOnce(
    of({
      status: 200,
      title: 'retry mock get data',
      data: { ...brokerEntity, state: SERVICE_STATE.RUNNING },
    }).pipe(delay(100)),
  );
  const mockResolve = jest.fn();
  const mockReject = jest.fn();

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a              ';
    // start 1 time, get 6 times, retry 5 times
    // => 100ms * 1 + 100ms * 6 + 31s = 31700ms
    const expected = '--a 31699ms (vu)';
    const subs = ['   ^---------------', '--^ 31699ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.startBroker.TRIGGER,
        payload: {
          values: brokerEntity,
          resolve: mockResolve,
          reject: mockReject,
        },
      },
    });
    const output$ = startBrokerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.startBroker.REQUEST,
        payload: {
          brokerId: bkId,
        },
      },
      v: {
        type: actions.startBroker.FAILURE,
        payload: {
          brokerId: bkId,
          data: brokerEntity,
          status: 200,
          title: `Failed to start broker ${brokerEntity.name}: Unable to confirm the status of the broker is running`,
        },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          brokerId: bkId,
          data: brokerEntity,
          status: 200,
          title: `Failed to start broker ${brokerEntity.name}: Unable to confirm the status of the broker is running`,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(mockResolve).not.toHaveBeenCalled();
    expect(mockReject).toHaveBeenCalled();
  });
});

it('start broker multiple times should be executed once', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a---a 1s a 10s ';
    const expected = '--a       199ms v';
    const subs = ['   ^----------------', '--^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.startBroker.TRIGGER,
        payload: { values: brokerEntity },
      },
    });
    const output$ = startBrokerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.startBroker.REQUEST,
        payload: {
          brokerId: bkId,
        },
      },
      v: {
        type: actions.startBroker.SUCCESS,
        payload: {
          brokerId: bkId,
          entities: {
            brokers: {
              [bkId]: {
                ...brokerEntity,
                state: SERVICE_STATE.RUNNING,
              },
            },
          },
          result: bkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('start different broker should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const anotherBrokerEntity = {
      ...brokerEntity,
      name: 'anotherbk',
      group: 'default',
      xms: 1111,
      xmx: 2222,
      clientPort: 3333,
    };
    const input = '   ^-a--b           ';
    const expected = '--a--b 196ms y--z';
    const subs = ['   ^----------------', '--^ 199ms !', '-----^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.startBroker.TRIGGER,
        payload: { values: brokerEntity },
      },
      b: {
        type: actions.startBroker.TRIGGER,
        payload: { values: anotherBrokerEntity },
      },
    });
    const output$ = startBrokerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.startBroker.REQUEST,
        payload: {
          brokerId: bkId,
        },
      },
      b: {
        type: actions.startBroker.REQUEST,
        payload: {
          brokerId: getId(anotherBrokerEntity),
        },
      },
      y: {
        type: actions.startBroker.SUCCESS,
        payload: {
          brokerId: bkId,
          entities: {
            brokers: {
              [bkId]: {
                ...brokerEntity,
                state: SERVICE_STATE.RUNNING,
              },
            },
          },
          result: bkId,
        },
      },
      z: {
        type: actions.startBroker.SUCCESS,
        payload: {
          brokerId: getId(anotherBrokerEntity),
          entities: {
            brokers: {
              [getId(anotherBrokerEntity)]: {
                ...anotherBrokerEntity,
                state: SERVICE_STATE.RUNNING,
              },
            },
          },
          result: getId(anotherBrokerEntity),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});
