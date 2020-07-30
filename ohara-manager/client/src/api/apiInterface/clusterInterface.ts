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

import { BasicResponse, ObjectKey, Metrics } from './basicInterface';

export enum SERVICE_STATE {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  FAILED = 'FAILED',
  UNKNOWN = 'UNKNOWN',
}

export interface ServiceBody extends ObjectKey {
  [k: string]: any;
}

export interface ClusterData {
  aliveNodes: string[];
  deadNodes?: string[];
  lastModified: number;
  state?: SERVICE_STATE;
  error?: string;
  nodeMetrics: {
    [hostname: string]: Metrics;
  };
  [k: string]: any;
}

export interface ClusterResponse extends BasicResponse {
  data: ClusterData;
}

export interface ClusterResponseList extends BasicResponse {
  data: ClusterData[];
}
