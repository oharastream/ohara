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

import { combineEpics } from 'redux-observable';
import createStreamEpic from './createStreamEpic';
import fetchStreamsEpic from './fetchStreamsEpic';
import updateStreamEpic from './updateStreamEpic';
import deleteStreamEpic from './deleteStreamEpic';
import deleteStreamsEpic from './deleteStreamsEpic';
import startStreamEpic from './startStreamEpic';
import stopStreamEpic from './stopStreamEpic';
import stopStreamsEpic from './stopStreamsEpic';
import removeStreamToLinkEpic from './removeStreamToLinkEpic';
import removeStreamFromLinkEpic from './removeStreamFromLinkEpic';
import updateLinkStreamEpic from './updateLinkStreamEpic';

export default combineEpics(
  createStreamEpic,
  fetchStreamsEpic,
  updateStreamEpic,
  deleteStreamEpic,
  deleteStreamsEpic,
  startStreamEpic,
  stopStreamEpic,
  stopStreamsEpic,
  removeStreamToLinkEpic,
  removeStreamFromLinkEpic,
  updateLinkStreamEpic,
);
