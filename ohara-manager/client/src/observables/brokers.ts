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
import * as brokerApi from 'api/brokerApi';
import { RETRY_STRATEGY } from 'const';
import { isServiceStarted, isServiceStopped } from './utils';

export function createBroker(values: any) {
  return defer(() => brokerApi.create(values)).pipe(map((res) => res.data));
}

export function fetchBroker(values: ObjectKey) {
  return defer(() => brokerApi.get(values)).pipe(map((res) => res.data));
}

export function startBroker(key: ObjectKey) {
  // try to start until the broker starts successfully
  return of(
    defer(() => brokerApi.start(key)),
    defer(() => brokerApi.get(key)).pipe(
      tap((res: ClusterResponse) => {
        if (!isServiceStarted(res.data)) {
          throw {
            ...res,
            title: `Failed to start broker ${key.name}: Unable to confirm the status of the broker is running`,
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

export function stopBroker(key: ObjectKey) {
  // try to stop until the broker really stops
  return of(
    defer(() => brokerApi.stop(key)),
    defer(() => brokerApi.get(key)).pipe(
      tap((res: ClusterResponse) => {
        if (!isServiceStopped(res.data)) {
          throw {
            ...res,
            title: `Failed to stop broker ${key.name}: Unable to confirm the status of the broker is not running`,
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

export function deleteBroker(key: ObjectKey) {
  return defer(() => brokerApi.remove(key));
}
