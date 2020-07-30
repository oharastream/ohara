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

import { createRoutine } from 'redux-routines';

export const fetchStreams = createRoutine('FETCH_STREAMS');
export const createStream = createRoutine('CREATE_STREAM');
export const deleteStream = createRoutine('DELETE_STREAM');
export const deleteStreams = createRoutine('DELETE_STREAMS');
export const updateStream = createRoutine('UPDATE_STREAM');
export const startStream = createRoutine('START_STREAM');
export const stopStream = createRoutine('STOP_STREAM');
export const stopStreams = createRoutine('STOP_STREAMS');
export const removeStreamToLink = createRoutine('REMOVE_STREAM_TO_LINK');
export const removeStreamFromLink = createRoutine('REMOVE_STREAM_FROM_LINK');
export const updateStreamLink = createRoutine('UPDATE_STREAM_LINK');
