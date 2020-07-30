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
import { version } from '../../../package.json';

export const entity = {
  author: 'root',
  brokerClusterKey: {
    group: 'default',
    name: 'bk',
  },
  name: 'streamtest1',
  xms: 1024,
  routes: {},
  lastModified: 0,
  tags: {},
  xmx: 1024,
  imageName: `oharastream/stream:${version}`,
  jarKey: {
    group: 'default',
    name: 'ohara-it-stream.jar',
  },
  to: [
    {
      group: 'default',
      name: 'topic1',
    },
  ],
  revision: 'b303f3c2e52647ee5e79e55f9d74a5e51238a92c',
  version: `${version}`,
  aliveNodes: [],
  'stream.class': 'oharastream.ohara.it.stream.DumbStream',
  from: [
    {
      group: 'default',
      name: 'topic0',
    },
  ],
  nodeMetrics: {},
  jmxPort: 44914,
  kind: 'stream',
  group: 'default',
  nodeNames: ['node00'],
};

export let entities = [
  entity,
  assign({}, entity, { name: 's2' }),
  assign({}, entity, { name: 'anotherstream' }),
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
    title: 'mock create stream data',
    data: { ...runtimeData, ...params },
  }).pipe(delay(100));

// simulate a promise request with a delay of 1s
export const remove = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock delete stream data',
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
    title: 'mock get all stream data',
    data: entities,
  }).pipe(delay(100));

// simulate a promise request with a delay of 500ms
export const get = (params: ObjectKey): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock get stream data',
    data: { ...entity, ...runtimeData, ...params },
  }).pipe(delay(100));

// simulate a promise request with a delay of 100ms
export const update = (params: ServiceBody): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock update stream data',
    data: { ...runtimeData, ...params },
  }).pipe(delay(100));

// simulate a promise request with a delay of 10ms
export const start = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock start stream data',
    data: { ...omit(runtimeData, 'state') },
  }).pipe(
    // to simulate stream is "started" in fetch request
    tap(() => (runtimeData = { ...runtimeData, state: SERVICE_STATE.RUNNING })),
    delay(100),
  );

// simulate a promise request with a delay of 10ms
export const stop = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock stop stream data',
    data: { ...omit(runtimeData, 'state') },
  }).pipe(
    // to simulate stream is "stopped" in fetch request
    tap(() => delete runtimeData.state),
    delay(100),
  );
