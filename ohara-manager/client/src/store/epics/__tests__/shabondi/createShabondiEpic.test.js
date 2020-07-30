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
import * as shabondiApi from 'api/shabondiApi';
import createShabondiEpic from '../../shabondi/createShabondiEpic';
import * as actions from 'store/actions';
import { getId } from 'utils/object';
import { entity as shabondiEntity } from 'api/__mocks__/shabondiApi';

jest.mock('api/shabondiApi');
const mockedPaperApi = jest.fn(() => {
  return {
    updateElement: () => noop(),
    removeElement: () => noop(),
  };
});
const paperApi = new mockedPaperApi();

const shabondiId = getId(shabondiEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

it('create shabondi should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a         ';
    const expected = '--a 99ms u';
    const subs = '    ^-----------';

    const action$ = hot(input, {
      a: {
        type: actions.createShabondi.TRIGGER,
        payload: {
          values: shabondiEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createShabondiEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createShabondi.REQUEST,
        payload: {
          shabondiId,
        },
      },
      u: {
        type: actions.createShabondi.SUCCESS,
        payload: {
          shabondiId,
          entities: {
            shabondis: {
              [shabondiId]: shabondiEntity,
            },
          },
          result: shabondiId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('create multiple shabondis should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-ab          ';
    const expected = '--ab 98ms uv';
    const subs = '    ^-------------';
    const anotherShabondiEntity = {
      ...shabondiEntity,
      name: 'anothershabondi',
    };

    const action$ = hot(input, {
      a: {
        type: actions.createShabondi.TRIGGER,
        payload: {
          values: shabondiEntity,
          options: { paperApi },
        },
      },
      b: {
        type: actions.createShabondi.TRIGGER,
        payload: {
          values: anotherShabondiEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createShabondiEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createShabondi.REQUEST,
        payload: {
          shabondiId,
        },
      },
      u: {
        type: actions.createShabondi.SUCCESS,
        payload: {
          shabondiId,
          entities: {
            shabondis: {
              [shabondiId]: shabondiEntity,
            },
          },
          result: shabondiId,
        },
      },
      b: {
        type: actions.createShabondi.REQUEST,
        payload: {
          shabondiId: getId(anotherShabondiEntity),
        },
      },
      v: {
        type: actions.createShabondi.SUCCESS,
        payload: {
          shabondiId: getId(anotherShabondiEntity),
          entities: {
            shabondis: {
              [getId(anotherShabondiEntity)]: anotherShabondiEntity,
            },
          },
          result: getId(anotherShabondiEntity),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('create same shabondi within period should be created once only', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-aa 10s a    ';
    const expected = '--a 99ms u--';
    const subs = '    ^-------------';

    const action$ = hot(input, {
      a: {
        type: actions.createShabondi.TRIGGER,
        payload: {
          values: shabondiEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createShabondiEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createShabondi.REQUEST,
        payload: {
          shabondiId,
        },
      },
      u: {
        type: actions.createShabondi.SUCCESS,
        payload: {
          shabondiId,
          entities: {
            shabondis: {
              [shabondiId]: shabondiEntity,
            },
          },
          result: shabondiId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();
  });
});

it('throw exception of create shabondi should also trigger event log action', () => {
  const error = {
    status: -1,
    data: {},
    title: 'mock create shabondi failed',
  };
  const spyCreate = jest
    .spyOn(shabondiApi, 'create')
    .mockReturnValueOnce(throwError(error));

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a-----|';
    const expected = '--(aeu)-|';
    const subs = '    ^-------!';

    const action$ = hot(input, {
      a: {
        type: actions.createShabondi.TRIGGER,
        payload: {
          values: shabondiEntity,
          options: { paperApi },
        },
      },
    });
    const output$ = createShabondiEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.createShabondi.REQUEST,
        payload: { shabondiId },
      },
      e: {
        type: actions.createShabondi.FAILURE,
        payload: { ...error, shabondiId },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          ...error,
          shabondiId,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(spyCreate).toHaveBeenCalled();
  });
});
