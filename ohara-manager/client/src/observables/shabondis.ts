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
import { Observable, defer, forkJoin, of } from 'rxjs';
import { concatAll, last, map, mapTo, mergeMap, tap } from 'rxjs/operators';
import { isEmpty, union, uniqWith } from 'lodash';
import { retryBackoff } from 'backoff-rxjs';

import { ObjectKey } from 'api/apiInterface/basicInterface';
import {
  ClusterData,
  ClusterResponse,
} from 'api/apiInterface/clusterInterface';
import * as shabondiApi from 'api/shabondiApi';
import { RETRY_STRATEGY } from 'const';
import { fetchPipelines } from 'observables';
import { getKey, isEqualByKey } from 'utils/object';
import { hashByGroupAndName } from 'utils/sha';
import { isServiceStarted, isServiceStopped } from './utils';

export function createShabondi(values: any): Observable<ClusterData> {
  return defer(() => shabondiApi.create(values)).pipe(map((res) => res.data));
}

export function fetchShabondi(values: ObjectKey): Observable<ClusterData> {
  return defer(() => shabondiApi.get(values)).pipe(map((res) => res.data));
}

export function fetchShabondis(
  pipelineKey: ObjectKey,
): Observable<ClusterData[]> {
  const shabondiGroup = hashByGroupAndName(pipelineKey.group, pipelineKey.name);
  return defer(() => shabondiApi.getAll({ group: shabondiGroup })).pipe(
    map((res) => res.data),
  );
}

export function startShabondi(key: ObjectKey) {
  // try to start until the shabondi starts successfully
  return of(
    defer(() => shabondiApi.start(key)),
    defer(() => shabondiApi.get(key)).pipe(
      tap((res: ClusterResponse) => {
        if (!isServiceStarted(res.data)) {
          throw {
            ...res,
            title: `Failed to start shabondi ${key.name}: Unable to confirm the status of the shabondi is running`,
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

export function deleteShabondi(key: ObjectKey) {
  return defer(() => shabondiApi.remove(key));
}

export function stopShabondi(key: ObjectKey) {
  // try to stop until the shabondi really stops
  return of(
    defer(() => shabondiApi.stop(key)),
    defer(() => shabondiApi.get(key)).pipe(
      tap((res: ClusterResponse) => {
        if (!isServiceStopped(res.data)) {
          throw {
            ...res,
            title: `Failed to stop shabondi ${key.name}: Unable to confirm the status of the shabondi is not running`,
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

// Fetch and stop all shabondis for this workspace
export function fetchAndStopShabondis(workspaceKey: ObjectKey) {
  return fetchPipelines(workspaceKey).pipe(
    mergeMap((pipelines) => {
      if (isEmpty(pipelines)) return of([]);
      return forkJoin(
        pipelines.map((pipeline) => {
          return fetchShabondis(getKey(pipeline));
        }),
      );
    }),
    map((shabondisArray: ClusterData[][]) => union(...shabondisArray)),
    map((shabondis: ClusterData[]) => uniqWith(shabondis, isEqualByKey)),
    mergeMap((shabondis: ClusterData[]) => {
      if (isEmpty(shabondis)) return of(shabondis);
      return forkJoin(
        shabondis.map((shabondi: ClusterData) =>
          stopShabondi(getKey(shabondi)),
        ),
      );
    }),
  );
}

// Fetch and delete all shabondis for this workspace
export function fetchAndDeleteShabondis(workspaceKey: ObjectKey) {
  return fetchPipelines(workspaceKey).pipe(
    mergeMap((pipelines) => {
      if (isEmpty(pipelines)) return of([]);
      return forkJoin(
        pipelines.map((pipeline) => {
          return fetchShabondis(getKey(pipeline));
        }),
      );
    }),
    map((shabondisArray) => union(...shabondisArray)),
    map((shabondis) => uniqWith(shabondis, isEqualByKey)),
    mergeMap((shabondis) => {
      if (isEmpty(shabondis)) return of(shabondis);
      return forkJoin(
        shabondis.map((shabondi) =>
          deleteShabondi(getKey(shabondi)).pipe(mapTo(shabondi)),
        ),
      );
    }),
  );
}
