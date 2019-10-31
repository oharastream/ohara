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

import {
  string,
  number,
  array,
  object,
  option,
  generatePort,
  generateName,
} from '../utils/validation';

export const request = () => {
  const name = [string, generateName];
  const group = [string];
  const imageName = [string, option];
  const clientPort = [number, generatePort];
  const electionPort = [number, generatePort];
  const peerPort = [number, generatePort];
  const nodeNames = [array];
  const tags = [object, option];

  return {
    name,
    group,
    imageName,
    clientPort,
    electionPort,
    peerPort,
    nodeNames,
    tags,
  };
};

export const response = () => {
  const aliveNodes = [array];
  const state = [string];
  const error = [string];
  const lastModified = [number];
  const settings = {
    name: [string],
    group: [string],
    imageName: [string],
    clientPort: [number],
    peerPort: [number],
    electionPort: [number],
    nodeNames: [array],
    tags: [object],
  };

  return { aliveNodes, state, error, lastModified, settings };
};
