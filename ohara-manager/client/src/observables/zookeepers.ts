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

/* eslint-disable no-throw-literal */
import { defer, of } from 'rxjs';
import { map, concatAll, last, tap } from 'rxjs/operators';
import { retryBackoff } from 'backoff-rxjs';
import { ObjectKey } from 'api/apiInterface/basicInterface';
import { ClusterResponse } from 'api/apiInterface/clusterInterface';
import * as zookeeperApi from 'api/zookeeperApi';
import { RETRY_STRATEGY } from 'const';
import { isServiceStarted, isServiceStopped } from './utils';

export function createZookeeper(values: any) {
  return defer(() => zookeeperApi.create(values)).pipe(map((res) => res.data));
}

export function fetchZookeeper(values: ObjectKey) {
  return defer(() => zookeeperApi.get(values)).pipe(map((res) => res.data));
}

export function startZookeeper(key: ObjectKey) {
  // try to start until the zookeeper starts successfully
  return of(
    defer(() => zookeeperApi.start(key)),
    defer(() => zookeeperApi.get(key)).pipe(
      tap((res: ClusterResponse) => {
        if (!isServiceStarted(res.data)) {
          throw {
            ...res,
            title: `Failed to start zookeeper ${key.name}: Unable to confirm the status of the zookeeper is running`,
          };
        }
      }),
      retryBackoff(RETRY_STRATEGY),
    ),
  ).pipe(
    concatAll(),
    last(),
    map((res) => res.data),
  );
}

export function stopZookeeper(key: ObjectKey) {
  // try to stop until the zookeeper really stops
  return of(
    defer(() => zookeeperApi.stop(key)),
    defer(() => zookeeperApi.get(key)).pipe(
      tap((res: ClusterResponse) => {
        if (!isServiceStopped(res.data)) {
          throw {
            ...res,
            title: `Failed to stop zookeeper ${key.name}: Unable to confirm the status of the zookeeper is not running`,
          };
        }
      }),
      retryBackoff(RETRY_STRATEGY),
    ),
  ).pipe(
    concatAll(),
    last(),
    map((res) => res.data),
  );
}

export function deleteZookeeper(key: ObjectKey) {
  return defer(() => zookeeperApi.remove(key));
}
