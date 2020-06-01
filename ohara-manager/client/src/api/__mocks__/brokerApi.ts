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
} from 'api/apiInterface/clusterInterface';
import { ObjectKey, BasicResponse } from 'api/apiInterface/basicInterface';

export const entity = {
  name: 'bk00',
  group: 'default',
  'offsets.topic.replication.factor': 1,
  xms: 1024,
  routes: {},
  'num.partitions': 1,
  'num.network.threads': 1,
  tags: {},
  xmx: 1024,
  imageName: 'oharastream/broker:0.10.0',
  'log.dirs': '/home/ohara/default/data',
  jmxPort: 42020,
  'num.io.threads': 1,
  clientPort: 39614,
  zookeeperClusterKey: {
    group: 'default',
    name: 'zk00',
  },
  nodeNames: ['node00'],
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
    title: 'mock create broker data',
    data: { ...(omit(runtimeData, 'state') as ClusterData), ...params },
  }).pipe(delay(2000));

// simulate a promise request with a delay of 1s
export const remove = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock delete broker data',
    data: {},
  }).pipe(delay(1000));

// simulate a promise request with a delay of 500ms
export const get = (params: ObjectKey): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock get broker data',
    data: { ...entity, ...runtimeData, ...params },
  }).pipe(delay(500));

// simulate a promise request with a delay of 100ms
export const update = (params: ServiceBody): Observable<ClusterResponse> =>
  of({
    status: 200,
    title: 'mock update broker data',
    data: { ...runtimeData, ...params },
  }).pipe(delay(100));

// simulate a promise request with a delay of 10ms
export const start = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock start broker data',
    data: { ...omit(runtimeData, 'state') },
  }).pipe(
    // to simulate broker is "started" in fetch request
    tap(() => (runtimeData = { ...runtimeData, state: SERVICE_STATE.RUNNING })),
    delay(10),
  );

// simulate a promise request with a delay of 10ms
export const stop = (): Observable<BasicResponse> =>
  of({
    status: 200,
    title: 'mock stop broker data',
    data: { ...omit(runtimeData, 'state') },
  }).pipe(
    // to simulate broker is "stopped" in fetch request
    tap(() => delete runtimeData.state),
    delay(10),
  );
