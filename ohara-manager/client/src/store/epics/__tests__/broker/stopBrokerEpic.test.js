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

import { TestScheduler } from 'rxjs/testing';
import { of } from 'rxjs';
import { delay } from 'rxjs/operators';

import stopBrokerEpic from '../../broker/stopBrokerEpic';
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

it('stop broker should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a        ';
    const expected = '--a 199ms v';
    const subs = ['   ^----------', '--^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.stopBroker.TRIGGER,
        payload: { values: brokerEntity },
      },
    });
    const output$ = stopBrokerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopBroker.REQUEST,
        payload: {
          brokerId: bkId,
        },
      },
      v: {
        type: actions.stopBroker.SUCCESS,
        payload: {
          brokerId: bkId,
          entities: {
            brokers: {
              [bkId]: brokerEntity,
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

it('stop broker failed after reach retry limit', () => {
  // mock a 20 times "failed stopped" result
  const spyGet = jest.spyOn(brokerApi, 'get');
  for (let i = 0; i < 20; i++) {
    spyGet.mockReturnValueOnce(
      of({
        status: 200,
        title: 'retry mock get data',
        data: { ...brokerEntity, state: SERVICE_STATE.RUNNING },
      }).pipe(delay(100)),
    );
  }
  // get result finally
  spyGet.mockReturnValueOnce(
    of({
      status: 200,
      title: 'retry mock get data',
      data: { ...brokerEntity },
    }).pipe(delay(100)),
  );

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a             ';
    // stop 1 time, get 6 times, retry 5 times
    // => 100ms * 1 + 100ms * 6 + 31s = 31700ms
    const expected = '--a 31699ms (vu)';
    const subs = [
      '               ^---------------------',
      '               --^ 31699ms !   ',
    ];

    const action$ = hot(input, {
      a: {
        type: actions.stopBroker.TRIGGER,
        payload: { values: brokerEntity },
      },
    });
    const output$ = stopBrokerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopBroker.REQUEST,
        payload: {
          brokerId: bkId,
        },
      },
      v: {
        type: actions.stopBroker.FAILURE,
        payload: {
          brokerId: bkId,
          data: {
            ...brokerEntity,
            state: SERVICE_STATE.RUNNING,
          },
          status: 200,
          title: `Failed to stop broker ${brokerEntity.name}: Unable to confirm the status of the broker is not running`,
        },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          brokerId: bkId,
          data: {
            ...brokerEntity,
            state: SERVICE_STATE.RUNNING,
          },
          status: 200,
          title: `Failed to stop broker ${brokerEntity.name}: Unable to confirm the status of the broker is not running`,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('stop broker multiple times should be executed once', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a---a 1s a 10s ';
    const expected = '--a       199ms v';
    const subs = ['   ^----------------', '--^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.stopBroker.TRIGGER,
        payload: { values: brokerEntity },
      },
    });
    const output$ = stopBrokerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopBroker.REQUEST,
        payload: {
          brokerId: bkId,
        },
      },
      v: {
        type: actions.stopBroker.SUCCESS,
        payload: {
          brokerId: bkId,
          entities: {
            brokers: {
              [bkId]: brokerEntity,
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

it('stop different broker should be worked correctly', () => {
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
        type: actions.stopBroker.TRIGGER,
        payload: { values: brokerEntity },
      },
      b: {
        type: actions.stopBroker.TRIGGER,
        payload: { values: anotherBrokerEntity },
      },
    });
    const output$ = stopBrokerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopBroker.REQUEST,
        payload: {
          brokerId: bkId,
        },
      },
      b: {
        type: actions.stopBroker.REQUEST,
        payload: {
          brokerId: getId(anotherBrokerEntity),
        },
      },
      y: {
        type: actions.stopBroker.SUCCESS,
        payload: {
          brokerId: bkId,
          entities: {
            brokers: {
              [bkId]: brokerEntity,
            },
          },
          result: bkId,
        },
      },
      z: {
        type: actions.stopBroker.SUCCESS,
        payload: {
          brokerId: getId(anotherBrokerEntity),
          entities: {
            brokers: {
              [getId(anotherBrokerEntity)]: anotherBrokerEntity,
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
