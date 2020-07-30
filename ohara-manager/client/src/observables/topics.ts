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
import { map, mergeMap, concatAll, last, tap, mapTo } from 'rxjs/operators';
import { retryBackoff } from 'backoff-rxjs';
import { isEmpty } from 'lodash';
import { ObjectKey } from 'api/apiInterface/basicInterface';
import { TopicData, TopicResponse } from 'api/apiInterface/topicInterface';
import * as topicApi from 'api/topicApi';
import { RETRY_STRATEGY } from 'const';
import { getKey } from 'utils/object';
import { hashByGroupAndName } from 'utils/sha';

export function createTopic(values: any): Observable<TopicData> {
  return defer(() => topicApi.create(values)).pipe(map((res) => res.data));
}

export function fetchTopic(values: ObjectKey): Observable<TopicData> {
  return defer(() => topicApi.get(values)).pipe(map((res) => res.data));
}

export function fetchTopics(workspaceKey: ObjectKey): Observable<TopicData[]> {
  const topicGroup = hashByGroupAndName(workspaceKey.group, workspaceKey.name);
  return defer(() => topicApi.getAll({ group: topicGroup })).pipe(
    map((res) => res.data),
  );
}

export function startTopic(key: ObjectKey) {
  // try to start until the topic really started
  return of(
    defer(() => topicApi.start(key)),
    defer(() => topicApi.get(key)).pipe(
      tap((res: TopicResponse) => {
        if (res?.data?.state !== 'RUNNING') {
          throw {
            ...res,
            title: `Failed to start topic ${key.name}: Unable to confirm the status of the topic is running`,
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

export function stopTopic(key: ObjectKey) {
  // try to stop until the topic really stopped
  return of(
    defer(() => topicApi.stop(key)),
    defer(() => topicApi.get(key)).pipe(
      tap((res: TopicResponse) => {
        if (res?.data?.state) {
          throw {
            ...res,
            title: `Failed to stop topic ${key.name}: Unable to confirm the status of the topic is not running`,
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

export function deleteTopic(key: ObjectKey) {
  return defer(() => topicApi.remove(key));
}

// Fetch and stop all topics for this workspace
export function fetchAndStopTopics(workspaceKey: ObjectKey) {
  return fetchTopics(workspaceKey).pipe(
    mergeMap((topics) => {
      if (isEmpty(topics)) return of(topics);
      return forkJoin(topics.map((topic) => stopTopic(getKey(topic))));
    }),
  );
}

// Fetch and start all topics for this workspace
export function fetchAndStartTopics(workspaceKey: ObjectKey) {
  return fetchTopics(workspaceKey).pipe(
    mergeMap((topics) => {
      if (isEmpty(topics)) return of(topics);
      return forkJoin(topics.map((topic) => startTopic(getKey(topic))));
    }),
  );
}

// Fetch and delete all topics for this workspace
export function fetchAndDeleteTopics(workspaceKey: ObjectKey) {
  return fetchTopics(workspaceKey).pipe(
    mergeMap((topics) => {
      if (isEmpty(topics)) return of(topics);
      return forkJoin(
        topics.map((topic) => deleteTopic(getKey(topic)).pipe(mapTo(topic))),
      );
    }),
  );
}
