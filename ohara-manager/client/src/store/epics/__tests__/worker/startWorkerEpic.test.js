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
import { of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { TestScheduler } from 'rxjs/testing';

import { LOG_LEVEL } from 'const';
import * as workerApi from 'api/workerApi';
import startWorkerEpic from '../../worker/startWorkerEpic';
import { entity as workerEntity } from 'api/__mocks__/workerApi';
import * as actions from 'store/actions';
import { getId } from 'utils/object';
import { SERVICE_STATE } from 'api/apiInterface/clusterInterface';

jest.mock('api/workerApi');

const wkId = getId(workerEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

beforeEach(() => {
  // ensure the mock data is as expected before each test
  jest.restoreAllMocks();
});

it('start worker should be worked correctly', () => {
  const mockResolve = jest.fn();
  const mockReject = jest.fn();

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a        ';
    const expected = '--a 199ms v';
    const subs = ['   ^----------', '--^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.startWorker.TRIGGER,
        payload: {
          values: workerEntity,
          resolve: mockResolve,
          reject: mockReject,
        },
      },
    });
    const output$ = startWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.startWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      v: {
        type: actions.startWorker.SUCCESS,
        payload: {
          workerId: wkId,
          entities: {
            workers: {
              [wkId]: { ...workerEntity, state: 'RUNNING' },
            },
          },
          result: wkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(mockResolve).toHaveBeenCalled();
    expect(mockResolve).toHaveBeenCalledWith({
      ...workerEntity,
      state: 'RUNNING',
    });
    expect(mockReject).not.toHaveBeenCalled();
  });
});

it('start worker failed after reach retry limit', () => {
  // mock a 20 times "failed started" results
  const spyGet = jest.spyOn(workerApi, 'get');
  for (let i = 0; i < 20; i++) {
    spyGet.mockReturnValueOnce(
      of({
        status: 200,
        title: 'retry mock get data',
        data: { ...omit(workerEntity, 'state') },
      }).pipe(delay(100)),
    );
  }
  // get result finally
  spyGet.mockReturnValueOnce(
    of({
      status: 200,
      title: 'retry mock get data',
      data: { ...workerEntity, state: SERVICE_STATE.RUNNING },
    }).pipe(delay(100)),
  );
  const mockResolve = jest.fn();
  const mockReject = jest.fn();

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a             ';
    // start 1 time, get 6 times, retry 5 times
    // => 100ms * 1 + 100ms * 6 + 31s = 31700ms
    const expected = '--a 31699ms (vu)';
    const subs = ['   ^---------------', '--^ 31699ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.startWorker.TRIGGER,
        payload: {
          values: workerEntity,
          resolve: mockResolve,
          reject: mockReject,
        },
      },
    });
    const output$ = startWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.startWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      v: {
        type: actions.startWorker.FAILURE,
        payload: {
          workerId: wkId,
          data: workerEntity,
          status: 200,
          title: `Failed to start worker ${workerEntity.name}: Unable to confirm the status of the worker is running`,
        },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          workerId: wkId,
          data: workerEntity,
          status: 200,
          title: `Failed to start worker ${workerEntity.name}: Unable to confirm the status of the worker is running`,
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

it('start worker multiple times should be executed once', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a---a 1s a 10s ';
    const expected = '--a       199ms v';
    const subs = ['   ^----------------', '--^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.startWorker.TRIGGER,
        payload: { values: workerEntity },
      },
    });
    const output$ = startWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.startWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      v: {
        type: actions.startWorker.SUCCESS,
        payload: {
          workerId: wkId,
          entities: {
            workers: {
              [wkId]: { ...workerEntity, state: 'RUNNING' },
            },
          },
          result: wkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('start different worker should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const anotherWorkerEntity = {
      ...workerEntity,
      name: 'anotherwk',
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
        type: actions.startWorker.TRIGGER,
        payload: { values: workerEntity },
      },
      b: {
        type: actions.startWorker.TRIGGER,
        payload: { values: anotherWorkerEntity },
      },
    });
    const output$ = startWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.startWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      b: {
        type: actions.startWorker.REQUEST,
        payload: {
          workerId: getId(anotherWorkerEntity),
        },
      },
      y: {
        type: actions.startWorker.SUCCESS,
        payload: {
          workerId: wkId,
          entities: {
            workers: {
              [wkId]: { ...workerEntity, state: 'RUNNING' },
            },
          },
          result: wkId,
        },
      },
      z: {
        type: actions.startWorker.SUCCESS,
        payload: {
          workerId: getId(anotherWorkerEntity),
          entities: {
            workers: {
              [getId(anotherWorkerEntity)]: {
                ...anotherWorkerEntity,
                state: 'RUNNING',
              },
            },
          },
          result: getId(anotherWorkerEntity),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});
