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

import React from 'react';
import AssignmentIcon from '@material-ui/icons/Assignment';
import VpnKeyIcon from '@material-ui/icons/VpnKey';
import LibraryBooksIcon from '@material-ui/icons/LibraryBooks';
import SettingsInputHdmiIcon from '@material-ui/icons/SettingsInputHdmi';
import WavesIcon from '@material-ui/icons/Waves';
import WarningIcon from '@material-ui/icons/Warning';
import StorageIcon from '@material-ui/icons/Storage';
import SubjectIcon from '@material-ui/icons/Subject';
import FolderOpenIcon from '@material-ui/icons/FolderOpen';
import UnarchiveIcon from '@material-ui/icons/Unarchive';
import PowerSettingsNewIcon from '@material-ui/icons/PowerSettingsNew';
import DeleteForeverIcon from '@material-ui/icons/DeleteForever';

import { useShouldBeRestartWorkspace } from 'hooks';
import { SETTINGS_COMPONENT_TYPE, KIND } from 'const';
import DeleteConfirmDialogContent from './DeleteConfirmDialogContent';
import AutofillPage from './pages/AutofillPage';
import BrokerNodesPage from './pages/BrokerNodesPage';
import StreamJarsPage from './pages/StreamJarsPage';
import TopicPage from './pages/TopicPage';
import WorkerNodesPage from './pages/WorkerNodesPage';
import WorkerPluginsPage from './pages/WorkerPluginsPage';
import WorkspaceFilesPage from './pages/WorkspaceFilesPage';
import WorkspaceNodesPage from './pages/WorkspaceNodesPage';
import ZookeeperNodesPage from './pages/ZookeeperNodesPage';

/*
    Available props for component
      1. SETTINGS_COMPONENT_TYPE.PAGE:
          - title
          - type
          - icon: optional
          - subTitle: optional
          - componentProps: 
            - children: a valid react children

      2. SETTINGS_COMPONENT_TYPE.DIALOG:
          - title
          - type
          - icon: optional
          - subTitle: optional
          - componentProps: all props that are available in Dialog component

      3. SETTINGS_COMPONENT_TYPE.CUSTOMIZED:
          - title
          - type
          - icon: optional
          - subTitle: optional
          - componentProps: 
            - handleClick: optional
            - children: a valid react children
*/

export const useConfig = ({
  deleteWorkspace,
  hasRunningServices,
  restartConfirmMessage,
  restartWorkspace,
  targetIsWorker,
  //targetIsBroker,
  targetIsWorkspace,
  workspace,
}) => {
  const [isDeleteEnabled, setIsDeleteEnabled] = React.useState(false);

  const {
    countOfChangedBrokerNodes,
    countOfChangedWorkerNodes,
    countOfChangedWorkerPlugins,
    countOfChangedWorkerSharedJars,
    countOfChangedZookeeperNodes,
  } = useShouldBeRestartWorkspace();

  const menu = [
    {
      items: [
        {
          icon: <SubjectIcon />,
          text: 'Topics',
        },
        {
          icon: <AssignmentIcon />,
          text: 'Autofill',
        },
      ],
    },
    {
      subHeader: 'services',
      items: [
        {
          icon: <VpnKeyIcon />,
          text: 'Zookeeper',
        },
        {
          icon: <LibraryBooksIcon />,
          text: 'Broker',
        },
        {
          icon: <SettingsInputHdmiIcon />,
          text: 'Worker',
        },
        {
          icon: <WavesIcon />,
          text: 'Stream',
        },
      ],
    },
    {
      subHeader: 'Advanced',
      items: [
        {
          icon: <StorageIcon />,
          text: 'Nodes',
        },
        {
          icon: <FolderOpenIcon />,
          text: 'Files',
        },
        {
          icon: <WarningIcon />,
          text: 'Danger Zone',
        },
      ],
    },
  ];

  const sections = [
    {
      heading: 'Topics',
      components: [
        {
          icon: <SubjectIcon />,
          title: 'Topics in this workspace',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <TopicPage />,
          },
        },
      ],
    },
    {
      heading: 'Autofill',
      components: [
        {
          icon: <AssignmentIcon />,
          title: 'Autofill',
          subTitle: 'For component settings',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <AutofillPage />,
          },
        },
      ],
    },
    {
      heading: 'Zookeeper',
      components: [
        // Feature is disabled because it's not implemented in 0.10
        // {
        //   icon: <TuneIcon />,
        //   title: 'Zookeeper settings',
        //   type: SETTINGS_COMPONENT_TYPE.PAGE,
        // },
        {
          badge: { count: countOfChangedZookeeperNodes },
          icon: <StorageIcon />,
          title: 'Zookeeper nodes',
          subTitle: 'Nodes running the zookeeper',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <ZookeeperNodesPage />,
          },
        },
      ],
    },
    {
      heading: 'Broker',
      components: [
        // Feature is disabled because it's not implemented in 0.10
        // {
        //   icon: <TuneIcon />,
        //   title: 'Broker settings',
        //   type: SETTINGS_COMPONENT_TYPE.PAGE,
        // },
        {
          badge: { count: countOfChangedBrokerNodes },
          icon: <StorageIcon />,
          title: 'Broker nodes',
          subTitle: 'Nodes running the broker',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <BrokerNodesPage />,
          },
        },
      ],
    },
    {
      heading: 'Worker',
      components: [
        // Feature is disabled because it's not implemented in 0.10
        // {
        //   icon: <TuneIcon />,
        //   title: 'Worker settings',
        //   type: SETTINGS_COMPONENT_TYPE.PAGE,
        // },
        {
          badge: { count: countOfChangedWorkerNodes },
          icon: <StorageIcon />,
          title: 'Worker nodes',
          subTitle: 'Nodes running the worker',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <WorkerNodesPage />,
          },
        },
        {
          badge: {
            count: countOfChangedWorkerPlugins + countOfChangedWorkerSharedJars,
          },
          icon: <UnarchiveIcon />,
          title: 'Worker plugins and shared jars',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <WorkerPluginsPage />,
          },
        },
      ],
    },
    {
      heading: 'Stream',
      components: [
        {
          icon: <UnarchiveIcon />,
          title: 'Stream jars',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <StreamJarsPage />,
          },
        },
      ],
    },
    {
      heading: 'Nodes',
      components: [
        {
          icon: <StorageIcon />,
          title: 'Workspace nodes',
          subTitle: 'Nodes in this workspace',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <WorkspaceNodesPage />,
          },
        },
      ],
    },
    {
      heading: 'Files',
      components: [
        {
          icon: <FolderOpenIcon />,
          title: 'Files in this workspace',
          type: SETTINGS_COMPONENT_TYPE.PAGE,
          componentProps: {
            children: <WorkspaceFilesPage />,
          },
        },
      ],
    },
    {
      heading: 'Danger Zone',
      components: [
        {
          icon: <PowerSettingsNewIcon />,
          title: 'Restart this worker',
          type: SETTINGS_COMPONENT_TYPE.DIALOG,
          componentProps: {
            children: restartConfirmMessage(KIND.worker),
            title: 'Are you absolutely sure?',
            confirmText: 'RESTART',
            onConfirm: () => {
              targetIsWorker();
              restartWorkspace();
            },
            maxWidth: 'sm',
            confirmDisabled: hasRunningServices,
            testId: 'restart-worker-confirm-dialog',
          },
        },
        // Waiting for support volume
        // {
        //   icon: <PowerSettingsNewIcon />,
        //   title: 'Restart this broker',
        //   type: SETTINGS_COMPONENT_TYPE.DIALOG,
        //   componentProps: {
        //     children: restartConfirmMessage(KIND.broker),
        //     title: 'Are you absolutely sure?',
        //     confirmText: 'RESTART',
        //     onConfirm: () => {
        //       targetIsBroker();
        //       restartWorkspace();
        //     },
        //     maxWidth: 'sm',
        //     confirmDisabled: hasRunningServices,
        //     testId: 'restart-broker-confirm-dialog',
        //   },
        // },
        {
          icon: <PowerSettingsNewIcon />,
          title: 'Restart this workspace',
          type: SETTINGS_COMPONENT_TYPE.DIALOG,
          componentProps: {
            children: restartConfirmMessage(),
            title: 'Are you absolutely sure?',
            confirmText: 'RESTART',
            onConfirm: () => {
              restartWorkspace();
              targetIsWorkspace();
            },
            maxWidth: 'sm',
            confirmDisabled: hasRunningServices,
            testId: 'restart-workspace-confirm-dialog',
          },
        },
        {
          icon: <DeleteForeverIcon />,
          title: 'Delete this workspace',
          type: SETTINGS_COMPONENT_TYPE.DIALOG,
          componentProps: {
            children: (
              <DeleteConfirmDialogContent
                onValidate={setIsDeleteEnabled}
                workspace={workspace}
              />
            ),
            title: 'Are you absolutely sure?',
            confirmDisabled: !isDeleteEnabled,
            confirmText: 'DELETE',
            maxWidth: 'sm',
            onConfirm: deleteWorkspace,
            testId: 'delete-workspace-confirm-dialog',
          },
        },
      ],
    },
  ];

  return createRefs(menu, sections);
};

function createRefs(menu, sections) {
  const sectionRefs = {};

  // Add a React ref object in each item
  const sectionsWithRefs = sections.map((section) => {
    const key = section.heading;
    sectionRefs[key] = React.createRef();

    return {
      ...section,
      ref: sectionRefs[key],
    };
  });

  const menuWithRefs = menu.map((section) => {
    return {
      ...section,
      items: section.items.map((item) => {
        return {
          ...item,
          ref: sectionRefs[item.text],
        };
      }),
    };
  });

  return {
    menu: menuWithRefs,
    sections: sectionsWithRefs,
  };
}
