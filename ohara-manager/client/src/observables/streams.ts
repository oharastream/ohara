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
import * as streamApi from 'api/streamApi';
import { RETRY_STRATEGY } from 'const';
import { fetchPipelines } from 'observables';
import { getKey, isEqualByKey } from 'utils/object';
import { hashByGroupAndName } from 'utils/sha';
import { isServiceStarted, isServiceStopped } from './utils';

export function createStream(values: any): Observable<ClusterData> {
  return defer(() => streamApi.create(values)).pipe(map((res) => res.data));
}

export function fetchStream(values: ObjectKey): Observable<ClusterData> {
  return defer(() => streamApi.get(values)).pipe(map((res) => res.data));
}

export function fetchStreams(
  pipelineKey: ObjectKey,
): Observable<ClusterData[]> {
  const streamGroup = hashByGroupAndName(pipelineKey.group, pipelineKey.name);
  return defer(() => streamApi.getAll({ group: streamGroup })).pipe(
    map((res) => res.data),
  );
}

export function startStream(key: ObjectKey) {
  // try to start until the stream starts successfully
  return of(
    defer(() => streamApi.start(key)),
    defer(() => streamApi.get(key)).pipe(
      tap((res: ClusterResponse) => {
        if (!isServiceStarted(res.data)) {
          throw {
            ...res,
            title: `Failed to start stream ${key.name}: Unable to confirm the status of the stream is running`,
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

export function stopStream(key: ObjectKey) {
  // try to stop until the stream really stops
  return of(
    defer(() => streamApi.stop(key)),
    defer(() => streamApi.get(key)).pipe(
      tap((res: ClusterResponse) => {
        if (!isServiceStopped(res.data)) {
          throw {
            ...res,
            title: `Failed to stop stream ${key.name}: Unable to confirm the status of the stream is not running`,
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

export function deleteStream(key: ObjectKey) {
  return defer(() => streamApi.remove(key));
}

// Fetch and stop all streams for this workspace
export function fetchAndStopStreams(workspaceKey: ObjectKey) {
  return fetchPipelines(workspaceKey).pipe(
    mergeMap((pipelines) => {
      if (isEmpty(pipelines)) return of([]);
      return forkJoin(
        pipelines.map((pipeline) => {
          return fetchStreams(getKey(pipeline));
        }),
      );
    }),
    map((streamsArray: ClusterData[][]) => union(...streamsArray)),
    map((streams: ClusterData[]) => uniqWith(streams, isEqualByKey)),
    mergeMap((streams: ClusterData[]) => {
      if (isEmpty(streams)) return of(streams);
      return forkJoin(
        streams.map((stream: ClusterData) => stopStream(getKey(stream))),
      );
    }),
  );
}

// Fetch and delete all streams for this workspace
export function fetchAndDeleteStreams(workspaceKey: ObjectKey) {
  return fetchPipelines(workspaceKey).pipe(
    mergeMap((pipelines) => {
      if (isEmpty(pipelines)) return of([]);
      return forkJoin(
        pipelines.map((pipeline) => {
          return fetchStreams(getKey(pipeline));
        }),
      );
    }),
    map((streamsArray: ClusterData[][]) => union(...streamsArray)),
    map((streams: ClusterData[]) => uniqWith(streams, isEqualByKey)),
    mergeMap((streams: ClusterData[]) => {
      if (isEmpty(streams)) return of(streams);
      return forkJoin(
        streams.map((stream: ClusterData) =>
          deleteStream(getKey(stream)).pipe(mapTo(stream)),
        ),
      );
    }),
  );
}
