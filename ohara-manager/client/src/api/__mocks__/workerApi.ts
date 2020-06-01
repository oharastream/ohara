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

import { omit } from 'lodash';
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
  brokerClusterKey: {
    group: 'default',
    name: 'bk00',
  },
  name: 'wk00',
  'offset.storage.partitions': 1,
  xms: 2048,
  routes: {},
  'config.storage.topic': 'b8dadc3de21048fa927335b8f',
  sharedJarKeys: [],
  tags: {},
  xmx: 2048,
  imageName: 'oharastream/connect-worker:0.10.0',
  'offset.storage.topic': '346b839ea3e74387ab1eea409',
  'status.storage.replication.factor': 1,
  'group.id': 'af4b4d49234a4848bb90fb452',
  'offset.storage.replication.factor': 1,
  pluginKeys: [],
  'status.storage.partitions': 1,
  freePorts: [],
  jmxPort: 33333,
  'config.storage.partitions': 1,
  clientPort: 45127,
  'config.storage.replication.factor': 1,
  group: 'default',
  nodeNames: ['node00'],
  'status.storage.topic': '1cdca943f0b945bc892ebe9a7',
  aliveNodes: [],
  lastModified: 0,
  nodeMetrics: {},
};

let runtimeData: ClusterData = {
  aliveNodes: [],
  lastModified: 0,
  nodeMetrics: {},
};

// simulate a promise request with a delay of 2s
export const create = (params: ServiceBody): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock create worker data',
    data: { ...(omit(runtimeData, 'state') as ClusterData), ...params },
  }).pipe(delay(2000));

// simulate a promise request with a delay of 1s
export const remove = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock delete worker data',
    data: {},
  }).pipe(delay(1000));

// simulate a promise request with a delay of 500ms
export const get = (params: ObjectKey): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock get worker data',
    data: { ...entity, ...runtimeData, ...params },
  }).pipe(delay(500));

// this mock function is used for deleteWorkspaceEpic
export const getAll = (): Observable<ClusterResponseList> =>
  of({
    status: 200,
    title: 'mock get all worker data',
    data: [],
  });

// simulate a promise request with a delay of 100ms
export const update = (params: ServiceBody): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock update worker data',
    data: { ...runtimeData, ...params },
  }).pipe(delay(100));

// simulate a promise request with a delay of 10ms
export const start = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock start worker data',
    data: { ...omit(runtimeData, 'state') },
  }).pipe(
    // to simulate worker is "started" in fetch request
    tap(() => (runtimeData = { ...runtimeData, state: SERVICE_STATE.RUNNING })),
    delay(10),
  );

// simulate a promise request with a delay of 10ms
export const stop = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock stop worker data',
    data: { ...omit(runtimeData, 'state') },
  }).pipe(
    // to simulate worker is "stopped" in fetch request
    tap(() => delete runtimeData.state),
    delay(10),
  );
