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

import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { TestScheduler } from 'rxjs/testing';
import { times } from 'lodash';

import * as topicApi from 'api/topicApi';
import { entity as topicEntity } from 'api/__mocks__/topicApi';
import { fetchTopic, startTopic } from 'observables/topics';

jest.mock('api/topicApi');

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

const RESPONSES = {
  success: {
    status: 200,
    title: 'retry mock get data',
    data: topicEntity,
  },
  successWithRunning: {
    status: 200,
    title: 'retry mock get data',
    data: { ...topicEntity, state: 'RUNNING' },
  },
  error: { status: -1, data: {}, title: 'mock get topic failed' },
};

it('get topic should be worked correctly', () => {
  makeTestScheduler().run(({ expectObservable }) => {
    const params = { group: topicEntity.group, name: topicEntity.name };

    const expected = '100ms (v|)';

    const output$ = fetchTopic(params);

    expectObservable(output$).toBe(expected, {
      v: topicEntity,
    });
  });
});

it('get topic should be failed - Retry limit reached', () => {
  makeTestScheduler().run(({ expectObservable }) => {
    jest.restoreAllMocks();
    const spyGet = jest.spyOn(topicApi, 'get');
    times(20, () => spyGet.mockReturnValueOnce(throwError(RESPONSES.error)));

    const params = { group: topicEntity.group, name: topicEntity.name };

    const expected = '#';

    const output$ = fetchTopic(params);

    expectObservable(output$).toBe(expected, null, RESPONSES.error);
  });
});

it('start topic should run in two minutes', () => {
  makeTestScheduler().run(({ expectObservable, flush }) => {
    jest.restoreAllMocks();
    const spyStart = jest.spyOn(topicApi, 'start');
    const spyGet = jest.spyOn(topicApi, 'get');
    times(5, () =>
      spyGet.mockReturnValueOnce(of(RESPONSES.success).pipe(delay(100))),
    );
    spyGet.mockReturnValueOnce(
      of(RESPONSES.successWithRunning).pipe(delay(100)),
    );

    const params = {
      group: topicEntity.group,
      name: topicEntity.name,
    };

    // start 6 times, get 6 times, retry 5 times
    // => 100ms * 6 + 100ms * 6 + 31s = 32200ms
    const expected = '32200ms (v|)';

    const output$ = startTopic(params, true);

    expectObservable(output$).toBe(expected, {
      v: RESPONSES.successWithRunning.data,
    });

    flush();

    expect(spyStart).toHaveBeenCalled();
    expect(spyGet).toHaveBeenCalled();
    expect(spyStart.mock.calls.length).toBe(6);
    expect(spyGet.mock.calls.length).toBe(6);
  });
});
