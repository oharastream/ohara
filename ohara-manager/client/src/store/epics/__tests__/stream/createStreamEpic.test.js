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

import { LOG_LEVEL } from 'const';
import * as streamApi from 'api/streamApi';
import createStreamEpic from '../../stream/createStreamEpic';
import * as actions from 'store/actions';
import { getId } from 'utils/object';
import { entity as streamEntity } from 'api/__mocks__/streamApi';

jest.mock('api/streamApi');
const mockedPaperApi = jest.fn(() => {
  return {
    updateElement: () => noop(),
    removeElement: () => noop(),
  };
});
const paperApi = new mockedPaperApi();

const streamId = getId(streamEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

it('create stream should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a         ';
    const expected = '--a 99ms u';
    const subs = '    ^-----------';

    const action$ = hot(input, {
      a: {
        type: actions.createStream.TRIGGER,
        payload: {
          values: streamEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createStreamEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createStream.REQUEST,
        payload: {
          streamId,
        },
      },
      u: {
        type: actions.createStream.SUCCESS,
        payload: {
          streamId,
          entities: {
            streams: {
              [streamId]: streamEntity,
            },
          },
          result: streamId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('create multiple streams should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-ab          ';
    const expected = '--ab 98ms uv';
    const subs = '    ^-------------';
    const anotherStreamEntity = {
      ...streamEntity,
      name: 'anotherstream',
    };

    const action$ = hot(input, {
      a: {
        type: actions.createStream.TRIGGER,
        payload: {
          values: streamEntity,
          options: { paperApi },
        },
      },
      b: {
        type: actions.createStream.TRIGGER,
        payload: {
          values: anotherStreamEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createStreamEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createStream.REQUEST,
        payload: {
          streamId,
        },
      },
      u: {
        type: actions.createStream.SUCCESS,
        payload: {
          streamId,
          entities: {
            streams: {
              [streamId]: streamEntity,
            },
          },
          result: streamId,
        },
      },
      b: {
        type: actions.createStream.REQUEST,
        payload: {
          streamId: getId(anotherStreamEntity),
        },
      },
      v: {
        type: actions.createStream.SUCCESS,
        payload: {
          streamId: getId(anotherStreamEntity),
          entities: {
            streams: {
              [getId(anotherStreamEntity)]: anotherStreamEntity,
            },
          },
          result: getId(anotherStreamEntity),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('create same stream within period should be created once only', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-aa 10s a    ';
    const expected = '--a 99ms u--';
    const subs = '    ^-------------';

    const action$ = hot(input, {
      a: {
        type: actions.createStream.TRIGGER,
        payload: {
          values: streamEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createStreamEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createStream.REQUEST,
        payload: {
          streamId,
        },
      },
      u: {
        type: actions.createStream.SUCCESS,
        payload: {
          streamId,
          entities: {
            streams: {
              [streamId]: streamEntity,
            },
          },
          result: streamId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('throw exception of create stream should also trigger event log action', () => {
  const error = {
    status: -1,
    data: {},
    title: 'mock create stream failed',
  };
  const spyCreate = jest
    .spyOn(streamApi, 'create')
    .mockReturnValueOnce(throwError(error));

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a-----|';
    const expected = '--(aeu)-|';
    const subs = '    ^-------!';

    const action$ = hot(input, {
      a: {
        type: actions.createStream.TRIGGER,
        payload: {
          values: streamEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createStreamEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createStream.REQUEST,
        payload: { streamId },
      },
      e: {
        type: actions.createStream.FAILURE,
        payload: { ...error, streamId },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          ...error,
          streamId,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(spyCreate).toHaveBeenCalled();
  });
});
