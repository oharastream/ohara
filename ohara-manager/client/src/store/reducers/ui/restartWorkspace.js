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

import * as actions from 'store/actions';

const defaultSteps = [
  'Stop Worker',
  'Stop Broker',
  'Stop Zookeeper',
  'Start Zookeeper',
  'Start Broker',
  'Start Worker',
];

const initialState = {
  isOpen: false,
  loading: false,
  isAutoClose: false,
  closeDisable: true,
  progress: {
    open: false,
    steps: defaultSteps,
    activeStep: 0,
    log: [],
    message: 'Start RestartWorkspace... (0% complete)',
    isPause: false,
  },
  lastUpdated: null,
  error: null,
};

export default function reducer(state = initialState, action) {
  const now = new Date(Date.now()).toLocaleString();
  switch (action.type) {
    case actions.openRestartWorkspace.TRIGGER:
      return {
        ...initialState,
        isAutoClose: false,
        closeDisable: true,
        isOpen: true,
      };
    case actions.closeRestartWorkspace.TRIGGER:
      return initialState;

    case actions.pauseRestartWorkspace.TRIGGER:
      return {
        ...state,
        progress: {
          ...state.progress,
          isPause: true,
          log: [...state.progress.log, { title: `${now} [SUSPEND]` }],
        },
      };
    case actions.resumeRestartWorkspace.TRIGGER:
      return {
        ...state,
        progress: {
          ...state.progress,
          isPause: true,
          log: [...state.progress.log, { title: `${now} [RESUME]` }],
        },
      };
    case actions.rollbackRestartWorkspace.TRIGGER:
      return {
        ...state,
        progress: {
          ...state.progress,
          log: [...state.progress.log, { title: `${now} [ROLLBACK]` }],
        },
      };
    case actions.autoCloseRestartWorkspace.TRIGGER:
      const isAuto = state.isAutoClose ? false : true;
      return {
        ...state,
        isAutoClose: isAuto,
      };
    case actions.updateZookeeper.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          message: 'Update zookeeper... (43% complete)',
          log: [...state.progress.log, { title: `${now} Update zookeeper...` }],
        },
      };
    case actions.startZookeeper.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          activeStep: 4,
          message: 'Start zookeeper success... (59% complete)',
          log: [
            ...state.progress.log,
            { title: `${now} Start zookeeper success...` },
          ],
        },
      };
    case actions.updateBroker.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          message: 'Update broker... (29% complete)',
          log: [...state.progress.log, { title: `${now} Update broker...` }],
        },
      };
    case actions.startBroker.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          activeStep: 5,
          message: 'Start broker success... (73% complete)',
          log: [
            ...state.progress.log,
            { title: `${now} Start broker success...` },
          ],
        },
      };
    case actions.updateWorker.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          message: 'Update worker... (15% complete)',
          log: [...state.progress.log, { title: `${now} Update worker...` }],
        },
      };
    case actions.startWorker.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          message: 'Start worker success... (87% complete)',
          log: [
            ...state.progress.log,
            { title: `${now} Start worker success...` },
          ],
        },
      };
    case actions.stopWorker.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          activeStep: 1,
          message: 'Stop worker success... (14% complete)',
          log: [
            ...state.progress.log,
            { title: `${now} Stop worker success...` },
          ],
        },
      };
    case actions.stopBroker.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          activeStep: 2,
          message: 'Stop broker success... (28% complete)',
          log: [
            ...state.progress.log,
            { title: `${now} Stop broker success...` },
          ],
        },
      };
    case actions.stopZookeeper.SUCCESS:
      return {
        ...state,
        progress: {
          ...state.progress,
          activeStep: 3,
          message: 'Stop zookeeper success... (42% complete)',
          log: [
            ...state.progress.log,
            {
              title: `${now} Stop zookeeper success...`,
            },
          ],
        },
      };
    case actions.restartWorkspace.SUCCESS:
      return {
        ...state,
        closeDisable: false,
        progress: {
          ...state.progress,
          activeStep: 6,
          message: 'Restart workspace success... (100% complete)',
          isPause: false,
        },
      };
    default:
      return state;
  }
}
