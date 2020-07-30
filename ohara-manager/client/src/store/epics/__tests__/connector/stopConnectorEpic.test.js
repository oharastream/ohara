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

import stopConnectorEpic from '../../connector/stopConnectorEpic';
import * as connectorApi from 'api/connectorApi';
import * as actions from 'store/actions';
import { getId } from 'utils/object';
import { entity as connectorEntity } from 'api/__mocks__/connectorApi';
import { State as ConnectorState } from 'api/apiInterface/connectorInterface';
import { LOG_LEVEL, CELL_STATUS } from 'const';

jest.mock('api/connectorApi');

const paperApi = {
  updateElement: jest.fn(),
  removeElement: jest.fn(),
  getCell: jest.fn(),
};

const connectorId = getId(connectorEntity);

const makeTestScheduler = () =>
  new TestScheduler((actual, expected) => {
    expect(actual).toEqual(expected);
  });

beforeEach(() => {
  jest.restoreAllMocks();
  jest.resetAllMocks();
});

it('should stop the connector', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a        ';
    const expected = '--a 199ms v';
    const subs = '    ^----------';
    const id = '1234';

    const action$ = hot(input, {
      a: {
        type: actions.stopConnector.TRIGGER,
        payload: {
          values: { ...connectorEntity, id },
          options: { paperApi },
        },
      },
    });
    const output$ = stopConnectorEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopConnector.REQUEST,
        payload: {
          connectorId,
        },
      },
      v: {
        type: actions.stopConnector.SUCCESS,
        payload: {
          connectorId,
          entities: {
            connectors: {
              [connectorId]: {
                ...connectorEntity,
                id,
              },
            },
          },
          result: connectorId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(paperApi.updateElement).toHaveBeenCalledTimes(2);
    expect(paperApi.updateElement).toHaveBeenCalledWith(id, {
      status: CELL_STATUS.pending,
    });
    expect(paperApi.updateElement).toHaveBeenCalledWith(id, {
      status: CELL_STATUS.stopped,
    });
  });
});

it('should fail after reaching the retry limit', () => {
  // mock a 20 times "failed stoped" result
  const spyGet = jest.spyOn(connectorApi, 'get');
  for (let i = 0; i < 20; i++) {
    spyGet.mockReturnValueOnce(
      of({
        status: 200,
        title: 'retry mock get data',
        data: { ...connectorEntity, state: ConnectorState.RUNNING },
      }).pipe(delay(100)),
    );
  }
  // get result finally
  spyGet.mockReturnValueOnce(
    of({
      status: 200,
      title: 'retry mock get data',
      data: { ...connectorEntity },
    }).pipe(delay(100)),
  );

  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a            ';
    // stop 6 times, get 6 times, retry 5 times
    // => 100ms * 6 + 100ms * 6 + 31s = 32200ms
    const expected = '--a 32199ms (vu)';
    const subs = '    ^--------------';
    const id = '1234';

    const action$ = hot(input, {
      a: {
        type: actions.stopConnector.TRIGGER,
        payload: {
          values: {
            ...connectorEntity,
            id,
          },
          options: { paperApi },
        },
      },
    });
    const output$ = stopConnectorEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopConnector.REQUEST,
        payload: {
          connectorId,
        },
      },
      v: {
        type: actions.stopConnector.FAILURE,
        payload: {
          connectorId,
          data: {
            ...connectorEntity,
            state: ConnectorState.RUNNING,
          },
          status: 200,
          title: `Failed to stop connector ${connectorEntity.name}: Unable to confirm the status of the connector is not running`,
        },
      },
      u: {
        type: actions.createEventLog.TRIGGER,
        payload: {
          connectorId,
          data: {
            ...connectorEntity,
            state: ConnectorState.RUNNING,
          },
          status: 200,
          title: `Failed to stop connector ${connectorEntity.name}: Unable to confirm the status of the connector is not running`,
          type: LOG_LEVEL.error,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(paperApi.updateElement).toHaveBeenCalledTimes(2);
    expect(paperApi.updateElement).toHaveBeenCalledWith(id, {
      status: CELL_STATUS.pending,
    });
    expect(paperApi.updateElement).toHaveBeenCalledWith(id, {
      status: CELL_STATUS.running,
    });
  });
});

it('stop connector multiple times should be worked once', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const input = '   ^-a---a 1s a 10s ';
    const expected = '--a       199ms v';
    const subs = '    ^----------------';
    const id = '1234';

    const action$ = hot(input, {
      a: {
        type: actions.stopConnector.TRIGGER,
        payload: {
          values: { ...connectorEntity, id },
          options: { paperApi },
        },
      },
    });
    const output$ = stopConnectorEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopConnector.REQUEST,
        payload: { connectorId },
      },
      v: {
        type: actions.stopConnector.SUCCESS,
        payload: {
          connectorId,
          entities: {
            connectors: {
              [connectorId]: {
                ...connectorEntity,
                id,
              },
            },
          },
          result: connectorId,
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(paperApi.updateElement).toHaveBeenCalledTimes(2);
    expect(paperApi.updateElement).toHaveBeenCalledWith(id, {
      status: CELL_STATUS.pending,
    });
    expect(paperApi.updateElement).toHaveBeenCalledWith(id, {
      status: CELL_STATUS.stopped,
    });
  });
});

it('stop different connector should be worked correctly', () => {
  makeTestScheduler().run((helpers) => {
    const { hot, expectObservable, expectSubscriptions, flush } = helpers;

    const anotherConnectorEntity = {
      ...connectorEntity,
      name: 'anotherconnector',
      group: 'default',
      xms: 1111,
      xmx: 2222,
      clientPort: 3333,
    };
    const input = '   ^-a--b           ';
    const expected = '--a--b 196ms y--z';
    const subs = '    ^----------------';
    const id1 = '1234';
    const id2 = '5678';

    const action$ = hot(input, {
      a: {
        type: actions.stopConnector.TRIGGER,
        payload: {
          values: { ...connectorEntity, id: id1 },
          options: { paperApi },
        },
      },
      b: {
        type: actions.stopConnector.TRIGGER,
        payload: {
          values: { ...anotherConnectorEntity, id: id2 },
          options: { paperApi },
        },
      },
    });
    const output$ = stopConnectorEpic(action$);

    expectObservable(output$).toBe(expected, {
      a: {
        type: actions.stopConnector.REQUEST,
        payload: {
          connectorId,
        },
      },
      b: {
        type: actions.stopConnector.REQUEST,
        payload: {
          connectorId: getId(anotherConnectorEntity),
        },
      },
      y: {
        type: actions.stopConnector.SUCCESS,
        payload: {
          connectorId,
          entities: {
            connectors: {
              [connectorId]: {
                ...connectorEntity,
                id: id1,
              },
            },
          },
          result: connectorId,
        },
      },
      z: {
        type: actions.stopConnector.SUCCESS,
        payload: {
          connectorId: getId(anotherConnectorEntity),
          entities: {
            connectors: {
              [getId(anotherConnectorEntity)]: {
                ...anotherConnectorEntity,
                id: id2,
              },
            },
          },
          result: getId(anotherConnectorEntity),
        },
      },
    });

    expectSubscriptions(action$.subscriptions).toBe(subs);

    flush();

    expect(paperApi.updateElement).toHaveBeenCalledTimes(4);
    expect(paperApi.updateElement).toHaveBeenCalledWith(id1, {
      status: CELL_STATUS.pending,
    });
    expect(paperApi.updateElement).toHaveBeenCalledWith(id1, {
      status: CELL_STATUS.stopped,
    });
  });
});
