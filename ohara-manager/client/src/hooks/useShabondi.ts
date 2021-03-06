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

import { useQuery, QueryResult } from 'react-query';
import { get } from 'api/shabondiApi';
import { Shabondi, Key } from 'types';
import { KIND } from 'const';

function getShabondi(_: Record<string, unknown>, key: Key): Promise<Shabondi> {
  return get(key).then((res) => res.data);
}

export default function useShabondi(
  nameOrKey: string | Key,
  config?: Record<string, any>,
): QueryResult<Shabondi> {
  let key: Key;
  if (typeof nameOrKey === 'string') {
    key = { name: nameOrKey, group: KIND.shabondi };
  } else {
    key = nameOrKey;
  }

  return useQuery(['shabondi', key], getShabondi, {
    enabled: !!key,
    ...config,
  });
}
