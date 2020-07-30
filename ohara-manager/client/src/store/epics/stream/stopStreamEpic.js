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

import { merge } from 'lodash';
import { normalize } from 'normalizr';
import { ofType } from 'redux-observable';
import { of } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  map,
  mergeMap,
  startWith,
  tap,
} from 'rxjs/operators';

import { CELL_STATUS, LOG_LEVEL } from 'const';
import * as actions from 'store/actions';
import * as schema from 'store/schema';
import { stopStream } from 'observables';
import { getId } from 'utils/object';

export default (action$) =>
  action$.pipe(
    ofType(actions.stopStream.TRIGGER),
    map((action) => action.payload),
    distinctUntilChanged(),
    mergeMap(({ values, options = {} }) => {
      const streamId = getId(values);
      const previousStatus =
        options.paperApi?.getCell(values?.id)?.status || CELL_STATUS.running;
      const updateStatus = (status) =>
        options.paperApi?.updateElement(values.id, {
          status,
        });

      updateStatus(CELL_STATUS.pending);
      return stopStream(values).pipe(
        tap(() => updateStatus(CELL_STATUS.stopped)),
        map((data) => normalize(data, schema.stream)),
        map((normalizedData) => merge(normalizedData, { streamId })),
        map((normalizedData) => actions.stopStream.success(normalizedData)),
        startWith(actions.stopStream.request({ streamId })),
        catchError((err) => {
          updateStatus(err?.data?.state?.toLowerCase() ?? previousStatus);
          return of(
            actions.stopStream.failure(merge(err, { streamId })),
            actions.createEventLog.trigger({ ...err, type: LOG_LEVEL.error }),
          );
        }),
      );
    }),
  );
