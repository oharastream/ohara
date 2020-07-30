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

import { useCallback, useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';

import _ from 'lodash';
import * as actions from 'store/actions';
import * as selectors from 'store/selectors';
import * as hooks from 'hooks';
import { hashByGroupAndName } from 'utils/sha';

export const useCreateConnectorAction = () => {
  const dispatch = useDispatch();
  const group = useConnectorGroup();
  return (values, options) =>
    dispatch(
      actions.createConnector.trigger({
        values: { ...values, group },
        options,
      }),
    );
};

export const useUpdateConnectorAction = () => {
  const dispatch = useDispatch();
  const group = useConnectorGroup();
  return (values, options) =>
    dispatch(
      actions.updateConnector.trigger({
        values: { ...values, group },
        options,
      }),
    );
};

export const useUpdateConnectorLinkAction = () => {
  const dispatch = useDispatch();
  const group = useConnectorGroup();
  return (values, options) =>
    dispatch(
      actions.updateConnectorLink.trigger({
        params: { ...values, group },
        options,
      }),
    );
};

export const useRemoveSourceLinkAction = () => {
  const dispatch = useDispatch();
  const group = useConnectorGroup();
  return (values, options) =>
    dispatch(
      actions.removeConnectorSourceLink.trigger({
        params: { ...values, group },
        options,
      }),
    );
};

export const useRemoveSinkLinkAction = () => {
  const dispatch = useDispatch();
  const group = useConnectorGroup();
  return (values, options) =>
    dispatch(
      actions.removeConnectorSinkLink.trigger({
        params: { ...values, group },
        options,
      }),
    );
};

export const useStartConnectorAction = () => {
  const dispatch = useDispatch();
  const group = useConnectorGroup();
  return (values, options) =>
    dispatch(
      actions.startConnector.trigger({
        values: { ...values, group },
        options,
      }),
    );
};

export const useStopConnectorAction = () => {
  const dispatch = useDispatch();
  const group = useConnectorGroup();
  return (values, options) =>
    dispatch(
      actions.stopConnector.trigger({
        values: { ...values, group },
        options,
      }),
    );
};

export const useStopConnectorsAction = () => {
  const dispatch = useDispatch();
  return useCallback(
    (workerKey) =>
      new Promise((resolve, reject) =>
        dispatch(
          actions.stopConnectors.trigger({
            values: { workerKey },
            resolve,
            reject,
          }),
        ),
      ),
    [dispatch],
  );
};

export const useDeleteConnectorAction = () => {
  const dispatch = useDispatch();
  const group = useConnectorGroup();
  return (values, options) =>
    dispatch(
      actions.deleteConnector.trigger({
        params: { ...values, group },
        options,
      }),
    );
};

export const useDeleteConnectorsInWorkspaceAction = () => {
  const dispatch = useDispatch();
  const workspaceKey = hooks.useWorkspaceKey();
  return useCallback(
    () =>
      new Promise((resolve, reject) =>
        dispatch(
          actions.deleteConnectors.trigger({
            values: { workspaceKey },
            resolve,
            reject,
          }),
        ),
      ),
    [dispatch, workspaceKey],
  );
};

export const useFetchConnectorsAction = () => {
  const dispatch = useDispatch();
  return () => dispatch(actions.fetchConnectors.trigger());
};

export const useIsConnectorLoaded = () => {
  const mapState = useCallback(
    (state) => !!state.ui.connector?.lastUpdated,
    [],
  );
  return useSelector(mapState);
};

export const useIsConnectorLoading = () => {
  const mapState = useCallback((state) => !!state.ui.connector?.loading, []);
  return useSelector(mapState);
};

export const useConnectorGroup = () => {
  const pipelineGroup = hooks.usePipelineGroup();
  const pipelineName = hooks.usePipelineName();
  if (pipelineGroup && pipelineName)
    return hashByGroupAndName(pipelineGroup, pipelineName);
};

export const useConnectors = () => {
  const isAppReady = hooks.useIsAppReady();
  const workerId = hooks.useWorkerId();
  const group = useConnectorGroup();
  const fetchConnectors = useFetchConnectorsAction();
  const isConnectorLoaded = useIsConnectorLoaded();
  const isConnectorLoading = useIsConnectorLoading();

  useEffect(() => {
    if (isConnectorLoaded || isConnectorLoading || !isAppReady) return;
    fetchConnectors();
  }, [fetchConnectors, isAppReady, isConnectorLoaded, isConnectorLoading]);

  return useSelector((state) => {
    const connectors = selectors.getConnectorByGroup(state, { group });
    const results = connectors.map((connector) => {
      const info = selectors.getInfoById(state, { id: workerId });

      const settingDefinitions =
        info?.classInfos.find(
          (def) => def.className === connector.connector__class,
        )?.settingDefinitions || [];

      return _.merge(connector, { settingDefinitions });
    });
    return results;
  });
};
