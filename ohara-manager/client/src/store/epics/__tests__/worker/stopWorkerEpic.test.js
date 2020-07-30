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

import { of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { TestScheduler } from 'rxjs/testing';

import { LOG_LEVEL } from 'const';
import * as workerApi from 'api/workerApi';
import stopWorkerEpic from '../../worker/stopWorkerEpic';
import { entity as workerEntity } from 'api/__mocks__/workerApi';
import * as actions from 'store/actions';
import { getId } from 'utils/object';
import { SERVICE_STATE } from 'api/apiInterface/clusterInterface';

jest.mock('api/connectorApi');
jest.mock('api/pipelineApi');
jest.mock('api/streamApi');
jest.mock('api/workerApi');

const wkId = getId(workerEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

let spyStopWorker;
let spyGetWorker;

beforeEach(() => {
  // ensure the mock data is as expected before each test
  jest.restoreAllMocks();
  spyStopWorker = jest.spyOn(workerApi, 'stop');
  spyGetWorker = jest.spyOn(workerApi, 'get');
});

it('stop worker should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a        ';
    const expected = '--a 199ms v';
    const subs = ['   ^----------', '--^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.stopWorker.TRIGGER,
        payload: { values: workerEntity },
      },
    });
    const output$ = stopWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      v: {
        type: actions.stopWorker.SUCCESS,
        payload: {
          workerId: wkId,
          entities: {
            workers: {
              [wkId]: workerEntity,
            },
          },
          result: wkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(spyStopWorker).toHaveBeenCalledTimes(1);
    expect(spyGetWorker).toHaveBeenCalledTimes(1);
  });
});

it('stop worker failed after reach retry limit', () => {
  // mock a 20 times "failed stopped" results
  for (let i = 0; i < 20; i++) {
    spyGetWorker.mockReturnValueOnce(
      of({
        status: 200,
        title: 'retry mock get data',
        data: { ...workerEntity, state: SERVICE_STATE.RUNNING },
      }).pipe(delay(100)),
    );
  }
  // get result finally
  spyGetWorker.mockReturnValueOnce(
    of({
      status: 200,
      title: 'retry mock get data',
      data: { ...workerEntity },
    }).pipe(delay(100)),
  );

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a                   ';
    // stop 1 time, get 6 times, retry 5 times
    // => 100ms * 1 + 100ms * 6 + 31s = 31700ms
    const expected = '--a 31699ms (vu)';
    const subs = [
      '               ^---------------------',
      '               --^ 31699ms !   ',
    ];

    const action$ = hot(input, {
      a: {
        type: actions.stopWorker.TRIGGER,
        payload: { values: workerEntity },
      },
    });
    const output$ = stopWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      v: {
        type: actions.stopWorker.FAILURE,
        payload: {
          workerId: wkId,
          data: {
            ...workerEntity,
            state: SERVICE_STATE.RUNNING,
          },
          status: 200,
          title: `Failed to stop worker ${workerEntity.name}: Unable to confirm the status of the worker is not running`,
        },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          workerId: wkId,
          data: {
            ...workerEntity,
            state: SERVICE_STATE.RUNNING,
          },
          status: 200,
          title: `Failed to stop worker ${workerEntity.name}: Unable to confirm the status of the worker is not running`,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(spyStopWorker).toHaveBeenCalledTimes(1);
    expect(spyGetWorker).toHaveBeenCalledTimes(6);
  });
});

it('stop worker multiple times should be executed once', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a---a 1s a 10s ';
    const expected = '--a 199ms v      ';
    const subs = [
      '               ^----------------',
      '               --^ 199ms !      ',
    ];

    const action$ = hot(input, {
      a: {
        type: actions.stopWorker.TRIGGER,
        payload: { values: workerEntity },
      },
    });
    const output$ = stopWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      v: {
        type: actions.stopWorker.SUCCESS,
        payload: {
          workerId: wkId,
          entities: {
            workers: {
              [wkId]: workerEntity,
            },
          },
          result: wkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(spyStopWorker).toHaveBeenCalledTimes(1);
    expect(spyGetWorker).toHaveBeenCalledTimes(1);
  });
});

it('stop different worker should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const anotherWorkerEntity = {
      ...workerEntity,
      name: 'anotherwk',
      group: 'default',
    };
    const input = '   ^-a--b                 ';
    const expected = '--a--b 196ms y--z';
    const subs = [
      '               ^----------------------',
      '               --^ 199ms !            ',
      '               -----^ 199ms !         ',
    ];

    const action$ = hot(input, {
      a: {
        type: actions.stopWorker.TRIGGER,
        payload: { values: workerEntity },
      },
      b: {
        type: actions.stopWorker.TRIGGER,
        payload: {
          values: anotherWorkerEntity,
        },
      },
    });
    const output$ = stopWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      b: {
        type: actions.stopWorker.REQUEST,
        payload: {
          workerId: getId(anotherWorkerEntity),
        },
      },
      y: {
        type: actions.stopWorker.SUCCESS,
        payload: {
          workerId: wkId,
          entities: {
            workers: {
              [wkId]: workerEntity,
            },
          },
          result: wkId,
        },
      },
      z: {
        type: actions.stopWorker.SUCCESS,
        payload: {
          workerId: getId(anotherWorkerEntity),
          entities: {
            workers: {
              [getId(anotherWorkerEntity)]: anotherWorkerEntity,
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
