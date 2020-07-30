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

import { omit, assign } from 'lodash';
import { of, Observable } from 'rxjs';
import { delay, tap } from 'rxjs/operators';
import {
  ClusterData,
  ClusterResponse,
  ServiceBody,
  SERVICE_STATE,
  ClusterResponseList,
} from 'api/apiInterface/clusterInterface';
import { ObjectKey, BasicResponse } from 'api/apiInterface/basicInterface';

export const entity = {
  'header.converter': 'org.apache.kafka.connect.converters.ByteArrayConverter',
  author: 'root',
  topicKeys: [
    {
      group: 'default',
      name: 't0',
    },
  ],
  name: 'perf',
  'check.rule': 'NONE',
  'key.converter': 'org.apache.kafka.connect.converters.ByteArrayConverter',
  lastModified: 0,
  tags: {},
  'value.converter': 'org.apache.kafka.connect.converters.ByteArrayConverter',
  'perf.cell.length': 10,
  'tasks.max': 1,
  'perf.batch': 10,
  'perf.frequency': '1000 milliseconds',
  'connector.class': 'oharastream.ohara.connector.perf.PerfSource',
  revision: 'baafe4a3d875e5e5028b686c4f74f26cfd8b1b66',
  version: '0.9.0',
  columns: [],
  metrics: {
    meters: [],
  },
  workerClusterKey: {
    group: 'default',
    name: 'wk',
  },
  tasksStatus: [],
  kind: 'source',
  group: 'default',
  aliveNodes: [],
  nodeMetrics: {},
};

export let entities = [
  entity,
  assign({}, entity, { name: 's2' }),
  assign({}, entity, { name: 'anotherconnector' }),
  assign({}, entity, { name: 's3', group: 'bar' }),
];

let runtimeData: ClusterData = {
  aliveNodes: [],
  lastModified: 0,
  nodeMetrics: {},
};

// simulate a promise request with a delay of 2s
export const create = (params: ServiceBody): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock create connector data',
    data: { ...runtimeData, ...params },
  }).pipe(delay(100));

// simulate a promise request with a delay of 1s
export const remove = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock delete connector data',
    data: {},
  }).pipe(
    // after remove, the element should be "invisible"
    tap(() => (entities = [])),
    delay(100),
  );

// simulate a promise request with a delay of 500ms
export const getAll = (): Observable<ClusterResponseList> =>
  of({
    status: 200,
    title: 'mock get all connector data',
    data: entities,
  }).pipe(delay(100));

// simulate a promise request with a delay of 500ms
export const get = (params: ObjectKey): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock get connector data',
    data: { ...entity, ...runtimeData, ...params },
  }).pipe(delay(100));

// simulate a promise request with a delay of 100ms
export const update = (params: ServiceBody): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock update connector data',
    data: { ...runtimeData, ...params },
  }).pipe(delay(100));

// simulate a promise request with a delay of 10ms
export const start = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock start connector data',
    data: { ...omit(runtimeData, 'state') },
  }).pipe(
    // to simulate connector is "started" in fetch request
    tap(() => (runtimeData = { ...runtimeData, state: SERVICE_STATE.RUNNING })),
    delay(100),
  );

// simulate a promise request with a delay of 10ms
export const stop = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock stop connector data',
    data: { ...omit(runtimeData, 'state') },
  }).pipe(
    // to simulate connector is "stopped" in fetch request
    tap(() => delete runtimeData.state),
    delay(100),
  );
