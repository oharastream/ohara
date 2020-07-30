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
  ConnectorResponse,
  Data as ConnectorData,
} from 'api/apiInterface/connectorInterface';
import * as connectorApi from 'api/connectorApi';
import { RETRY_STRATEGY } from 'const';
import { fetchPipelines } from 'observables';
import { hashByGroupAndName } from 'utils/sha';
import { getKey, isEqualByKey } from 'utils/object';
import { isServiceStarted, isServiceStopped } from './utils';

export function createConnector(values: any) {
  return defer(() => connectorApi.create(values)).pipe(map((res) => res.data));
}

export function fetchConnector(values: ObjectKey) {
  return defer(() => connectorApi.get(values)).pipe(map((res) => res.data));
}

export function fetchConnectors(
  pipelineKey: ObjectKey,
): Observable<ConnectorData[]> {
  const connectorGroup = hashByGroupAndName(
    pipelineKey.group,
    pipelineKey.name,
  );
  return defer(() => connectorApi.getAll({ group: connectorGroup })).pipe(
    map((res) => res.data),
  );
}

export function startConnector(key: ObjectKey) {
  // try to start until the connector starts successfully
  return of(
    defer(() => connectorApi.start(key)),
    defer(() => connectorApi.get(key)).pipe(
      tap((res: ConnectorResponse) => {
        if (!isServiceStarted(res.data)) {
          throw {
            ...res,
            title: `Failed to start connector ${key.name}: Unable to confirm the status of the connector is running`,
          };
        }
      }),
    ),
  ).pipe(
    concatAll(),
    last(),
    map((res) => res.data),
    retryBackoff({
      ...RETRY_STRATEGY,
      shouldRetry: (error) => {
        if (error.status === 400) return false;
        return true;
      },
    }),
  );
}

export function stopConnector(key: ObjectKey) {
  // try to stop until the connector really stops
  return of(
    defer(() => connectorApi.stop(key)),
    defer(() => connectorApi.get(key)).pipe(
      tap((res: ConnectorResponse) => {
        if (!isServiceStopped(res.data)) {
          throw {
            ...res,
            title: `Failed to stop connector ${key.name}: Unable to confirm the status of the connector is not running`,
          };
        }
      }),
    ),
  ).pipe(
    concatAll(),
    last(),
    map((res) => res.data),
    retryBackoff(RETRY_STRATEGY),
  );
}

export function deleteConnector(key: ObjectKey) {
  return defer(() => connectorApi.remove(key));
}

// Fetch and stop all connectors for this workspace
export function fetchAndStopConnectors(workspaceKey: ObjectKey) {
  return fetchPipelines(workspaceKey).pipe(
    mergeMap((pipelines) => {
      if (isEmpty(pipelines)) return of([]);
      return forkJoin(
        pipelines.map((pipeline) => fetchConnectors(getKey(pipeline))),
      );
    }),
    map((connectorsArray: ConnectorData[][]) => union(...connectorsArray)),
    map((connectors: ConnectorData[]) => uniqWith(connectors, isEqualByKey)),
    mergeMap((connectors: ConnectorData[]) => {
      if (isEmpty(connectors)) return of(connectors);
      return forkJoin(
        connectors.map((connector: ConnectorData) =>
          stopConnector(getKey(connector)),
        ),
      );
    }),
  );
}

// Fetch and delete all connectors for this workspace
export function fetchAndDeleteConnectors(workspaceKey: ObjectKey) {
  return fetchPipelines(workspaceKey).pipe(
    mergeMap((pipelines) => {
      if (isEmpty(pipelines)) return of([]);
      return forkJoin(
        pipelines.map((pipeline) => {
          return fetchConnectors(getKey(pipeline));
        }),
      );
    }),
    map((connectorsArray) => union(...connectorsArray)),
    map((connectors) => uniqWith(connectors, isEqualByKey)),
    mergeMap((connectors) => {
      if (isEmpty(connectors)) return of(connectors);
      return forkJoin(
        connectors.map((connector) =>
          deleteConnector(getKey(connector)).pipe(mapTo(connector)),
        ),
      );
    }),
  );
}
