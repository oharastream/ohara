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
import * as inspectApi from 'api/inspectApi';
import inspectWorkerEpic from '../../worker/inspectWorkerEpic';
import { entity as workerEntity } from 'api/__mocks__/workerApi';
import { workerInfoEntity } from 'api/__mocks__/inspectApi';
import * as actions from 'store/actions';
import { getId, getKey } from 'utils/object';

jest.mock('api/inspectApi');

const key = getKey(workerEntity);
const wkId = getId(workerEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

it('should inspect a worker', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a 10s     ';
    const expected = '--a 2999ms u';
    const subs = ['^-----------', '--^ 2999ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.inspectWorker.TRIGGER,
        payload: key,
      },
    });
    const output$ = inspectWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.inspectWorker.REQUEST,
        payload: {
          workerId: wkId,
        },
      },
      u: {
        type: actions.inspectWorker.SUCCESS,
        payload: {
          entities: {
            infos: {
              [wkId]: { ...workerInfoEntity, ...key },
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

it('inspect worker failed after reach retry limit', () => {
  // mock a 20 times "failed stopped" result
  const spyGet = jest.spyOn(inspectApi, 'getWorkerInfo');
  for (let i = 0; i < 10; i++) {
    spyGet.mockReturnValueOnce(
      of({
        status: 200,
        title: 'retry mock get data',
        data: { ...workerInfoEntity, classInfos: [] },
      }).pipe(delay(100)),
    );
  }
  // get result finally
  spyGet.mockReturnValueOnce(
    of({
      status: 200,
      title: 'retry mock get data',
      data: { ...workerInfoEntity, classInfos: [] },
    }).pipe(delay(100)),
  );

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a             ';
    // get 6 times, retry 5 times
    // => 100ms * 6 + 31s = 31600ms
    const expected = '--a 31599ms (vu)';
    const subs = ['   ^------------', '--^ 31599ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.inspectWorker.TRIGGER,
        payload: key,
      },
    });
    const output$ = inspectWorkerEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.inspectWorker.REQUEST,
        payload: {
          workerId: getId(key),
        },
      },
      v: {
        payload: {
          data: {
            ...workerInfoEntity,
            classInfos: [],
          },
          status: 200,
          title: 'retry mock get data',
          workerId: 'default_wk00',
        },
        type: 'INSPECT_WORKER/FAILURE',
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          data: {
            ...workerInfoEntity,
            classInfos: [],
          },
          status: 200,
          title: 'retry mock get data',
          type: LOG_LEVEL.error,
          workerId: wkId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});
