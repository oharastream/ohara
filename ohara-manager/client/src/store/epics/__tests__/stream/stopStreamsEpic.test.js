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
import { TestScheduler } from 'rxjs/testing';
import { keyBy } from 'lodash';

import * as pipelineApi from 'api/pipelineApi';
import * as streamApi from 'api/streamApi';
import stopStreamsEpic from '../../stream/stopStreamsEpic';
import { entity as workspaceEntity } from 'api/__mocks__/workspaceApi';
import { entities as pipelineEntities } from 'api/__mocks__/pipelineApi';
import { entities as streamEntities } from 'api/__mocks__/streamApi';
import { getId } from 'utils/object';
import * as actions from 'store/actions';
import { ENTITY_TYPE } from 'store/schema';

jest.mock('api/pipelineApi');
jest.mock('api/streamApi');

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

let spyGetAllPipelines;
let spyGetAllStreams;
let spyStopStream;

beforeEach(() => {
  // ensure the mock data is as expected before each test
  jest.restoreAllMocks();

  spyGetAllPipelines = jest.spyOn(pipelineApi, 'getAll').mockReturnValue(
    of({
      status: 200,
      title: 'mock get all pipelines',
      data: pipelineEntities,
    }),
  );

  spyGetAllStreams = jest.spyOn(streamApi, 'getAll').mockReturnValue(
    of({
      status: 200,
      title: 'mock get all streams',
      data: streamEntities,
    }),
  );

  spyStopStream = jest.spyOn(streamApi, 'stop');
});

it('stop streams should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a        ';
    const expected = '--a 199ms v';
    const subs = ['   ^----------', '--^ 199ms !'];

    const action$ = hot(input, {
      a: {
        type: actions.stopStreams.TRIGGER,
        payload: {
          values: {
            workspaceKey: {
              name: workspaceEntity.name,
              group: workspaceEntity.group,
            },
          },
        },
      },
    });
    const output$ = stopStreamsEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopStreams.REQUEST,
      },
      v: {
        type: actions.stopStreams.SUCCESS,
        payload: {
          entities: {
            [ENTITY_TYPE.streams]: keyBy(streamEntities, (e) => getId(e)),
          },
          result: streamEntities.map((e) => getId(e)),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(spyGetAllPipelines).toHaveBeenCalledTimes(1);
    expect(spyGetAllStreams).toHaveBeenCalledTimes(3);
    expect(spyStopStream).toHaveBeenCalledTimes(4);
  });
});
