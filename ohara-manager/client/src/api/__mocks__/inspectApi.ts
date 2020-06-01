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

import { of, Observable } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  InspectServiceResponse,
  InspectTopicResponse,
} from 'api/apiInterface/inspectInterface';
import {
  Type,
  Necessary,
  Reference,
  Permission,
} from 'api/apiInterface/definitionInterface';

export const brokerInfoEntity = {
  imageName: 'oharastream/broker:0.10.0',
  settingDefinitions: [
    {
      blacklist: [],
      reference: Reference.NONE,
      displayName: 'xmx',
      internal: false,
      permission: Permission.EDITABLE,
      documentation: 'maximum memory allocation (in MB)',
      necessary: Necessary.OPTIONAL,
      valueType: Type.POSITIVE_LONG,
      tableKeys: [],
      orderInGroup: 8,
      key: 'xmx',
      defaultValue: 1024,
      recommendedValues: [],
      group: 'core',
    },
  ],
  classInfos: [],
};

export const zookeeperInfoEntity = {
  imageName: 'oharastream/zookeeper:0.10.0',
  settingDefinitions: [
    {
      blacklist: [],
      reference: Reference.NONE,
      displayName: 'peerPort',
      internal: false,
      permission: Permission.EDITABLE,
      documentation: 'the port exposed to each quorum',
      necessary: Necessary.RANDOM_DEFAULT,
      valueType: Type.BINDING_PORT,
      tableKeys: [],
      orderInGroup: 10,
      key: 'peerPort',
      defaultValue: null,
      recommendedValues: [],
      group: 'core',
    },
  ],
  classInfos: [],
};

export const workerInfoEntity = {
  classInfos: [
    {
      className: 'oharastream.ohara.connector.console.ConsoleSink',
      classType: 'sink',
      settingDefinitions: [
        {
          blacklist: [],
          displayName: 'Connector name',
          documentation: 'the name of this connector',
          group: 'core',
          internal: false,
          key: 'name',
          necessary: Necessary.RANDOM_DEFAULT,
          orderInGroup: 2,
          permission: Permission.CREATE_ONLY,
          recommendedValues: [],
          reference: Reference.NONE,
          tableKeys: [],
          valueType: Type.STRING,
        },
      ],
    },
  ],
  imageName: 'oharastream/connect-worker:0.10.0',
  settingDefinitions: [
    {
      blacklist: [],
      displayName: 'sharedJarKeys',
      documentation: 'the shared jars',
      group: 'core',
      internal: false,
      key: 'sharedJarKeys',
      necessary: Necessary.OPTIONAL,
      orderInGroup: 12,
      permission: Permission.EDITABLE,
      recommendedValues: [],
      reference: Reference.FILE,
      tableKeys: [],
      valueType: Type.OBJECT_KEYS,
    },
  ],
};

export const shabondiInfoEntity = {
  classInfos: [],
  imageName: 'oharastream/shabondi:0.10.0',
  settingDefinitions: [
    {
      blacklist: [],
      displayName: 'Broker cluster key',
      documentation:
        'the key of broker cluster used to transfer data for this Shabondi',
      group: 'core',
      internal: false,
      key: 'brokerClusterKey',
      necessary: Necessary.REQUIRED,
      orderInGroup: 6,
      permission: Permission.EDITABLE,
      recommendedValues: [],
      reference: Reference.BROKER_CLUSTER,
      tableKeys: [],
      valueType: Type.OBJECT_KEY,
    },
  ],
};

export const topicEntity = {
  messages: [
    { partition: 1, offset: 0, value: { r1: 'fake topic data' } },
    { partition: 2, offset: 1, value: { col: 10 } },
  ],
};

// simulate a promise request with a delay of 3s
export const getZookeeperInfo = (): Observable<InspectServiceResponse> =>
  of({
    status: 200,
    title: 'mock inspect zookeeper data',
    data: zookeeperInfoEntity,
  }).pipe(delay(3000));

// simulate a promise request with a delay of 3s
export const getBrokerInfo = (): Observable<InspectServiceResponse> =>
  of({
    status: 200,
    title: 'mock inspect broker data',
    data: brokerInfoEntity,
  }).pipe(delay(3000));

// simulate a promise request with a delay of 3s
export const getWorkerInfo = (): Observable<InspectServiceResponse> =>
  of({
    status: 200,
    title: 'mock inspect worker data',
    data: workerInfoEntity,
  }).pipe(delay(3000));

export const getShabondiInfo = (): Observable<InspectServiceResponse> =>
  of({
    status: 200,
    title: 'mock inspect shabondi data',
    data: shabondiInfoEntity,
  });

// simulate a promise request with a delay of 5s
export const getTopicData = (): Observable<InspectTopicResponse> =>
  of({
    status: 200,
    title: 'mock inspect topic data',
    data: topicEntity,
  }).pipe(delay(5000));
