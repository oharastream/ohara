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

import { noop } from 'rxjs';
import { TestScheduler } from 'rxjs/testing';

import * as actions from 'store/actions';
import deletePipelineEpic from '../../pipeline/deletePipelineEpic';
import { getId } from 'utils/object';
import { KIND, CELL_STATUS } from 'const';
import { entity as pipelineEntity } from 'api/__mocks__/pipelineApi';
import { entity as connectorEntity } from 'api/__mocks__/connectorApi';
import { entity as streamEntity } from 'api/__mocks__/streamApi';
import { entity as topicEntity } from 'api/__mocks__/topicApi';
import { entity as shabondiEntity } from 'api/__mocks__/shabondiApi';

jest.mock('api/pipelineApi');
jest.mock('api/connectorApi');
jest.mock('api/streamApi');
jest.mock('api/topicApi');
jest.mock('api/shabondiApi');

const mockedPaperApi = jest.fn(() => {
  return {
    updateElement: () => noop(),
    removeElement: () => noop(),
  };
});

const paperApi = new mockedPaperApi();
const cells = [
  {
    kind: KIND.stream,
    name: streamEntity.name,
    group: streamEntity.group,
    className: streamEntity['stream.class'],
    status: CELL_STATUS.stopped,
  },
  {
    kind: KIND.source,
    name: connectorEntity.name,
    group: connectorEntity.group,
    className: connectorEntity['connector.class'],
    status: CELL_STATUS.stopped,
  },
  {
    kind: KIND.source,
    name: shabondiEntity.name,
    group: shabondiEntity.group,
    className: shabondiEntity.shabondi__class,
    status: CELL_STATUS.stopped,
  },
  {
    kind: KIND.topic,
    name: topicEntity.name,
    group: topicEntity.group,
    status: CELL_STATUS.running,
  },
];

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

it('should delete a pipeline', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    // prettier-ignore
    const input = '   ^-a                                                         ';
    // prettier-ignore
    const expected =  '-- 100ms a 99ms b 99ms c 99ms d 99ms e 99ms (fg) 496ms (hi)';
    // prettier-ignore
    const subs = '    ^-----------------------------------------------------------';

    const action$ = hot(input, {
      a: {
        type: actions.deletePipeline.TRIGGER,
        payload: {
          params: {
            name: pipelineEntity.name,
            group: pipelineEntity.group,
            cells,
          },
          options: { paperApi },
        },
      },
    });
    const output$ = deletePipelineEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.deleteConnector.SUCCESS,
        payload: {
          connectorId: getId(connectorEntity),
        },
      },
      b: {
        type: actions.deleteShabondi.SUCCESS,
        payload: getId(shabondiEntity.name, shabondiEntity.group),
      },
      c: {
        type: actions.deleteStream.SUCCESS,
        payload: {
          streamId: getId(streamEntity),
        },
      },
      d: {
        type: actions.stopTopic.REQUEST,
        payload: {
          kind: KIND.topic,
          name: topicEntity.name,
          group: topicEntity.group,
          status: CELL_STATUS.running,
        },
      },
      e: {
        type: actions.stopTopic.SUCCESS,
        payload: topicEntity,
      },
      f: {
        type: actions.deleteTopic.SUCCESS,
        payload: {
          topicId: getId(topicEntity),
        },
      },
      g: {
        type: actions.deletePipeline.REQUEST,
        payload: {
          pipelineId: getId(pipelineEntity),
        },
      },
      h: {
        type: actions.deletePipeline.SUCCESS,
        payload: {
          pipelineId: getId(pipelineEntity),
        },
      },
      i: {
        type: actions.switchPipeline.TRIGGER,
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});
