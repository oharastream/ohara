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
import { useCallback, useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';

import * as actions from 'store/actions';
import * as selectors from 'store/selectors';
import * as hooks from 'hooks';
import { hashByGroupAndName } from 'utils/sha';

export const useCreateShabondiAction = () => {
  const dispatch = useDispatch();
  const group = useShabondiGroup();
  return (values, options) =>
    dispatch(
      actions.createShabondi.trigger({
        values: { ...values, group },
        options,
      }),
    );
};

export const useUpdateShabondiAction = () => {
  const dispatch = useDispatch();
  const group = useShabondiGroup();
  return (values, options) =>
    dispatch(
      actions.updateShabondi.trigger({
        values: { ...values, group },
        options,
      }),
    );
};

export const useUpdateShabondiLinkAction = () => {
  const dispatch = useDispatch();
  const group = useShabondiGroup();
  return (params, options) =>
    dispatch(
      actions.updateShabondiLink.trigger({
        params: { ...params, group },
        options,
      }),
    );
};

export const useRemoveShabondiSourceLinkAction = () => {
  const dispatch = useDispatch();
  const group = useShabondiGroup();
  return (params, options) =>
    dispatch(
      actions.removeShabondiSourceLink.trigger({
        params: { ...params, group },
        options,
      }),
    );
};

export const useRemoveShabondiSinkLinkAction = () => {
  const dispatch = useDispatch();
  const group = useShabondiGroup();
  return (params, options) =>
    dispatch(
      actions.removeShabondiSinkLink.trigger({
        params: { ...params, group },
        options,
      }),
    );
};

export const useStartShabondiAction = () => {
  const dispatch = useDispatch();
  const group = useShabondiGroup();
  return (values, options) =>
    dispatch(
      actions.startShabondi.trigger({
        values: { ...values, group },
        options,
      }),
    );
};

export const useStopShabondiAction = () => {
  const dispatch = useDispatch();
  const group = useShabondiGroup();
  return (values, options) =>
    dispatch(
      actions.stopShabondi.trigger({
        values: { ...values, group },
        options,
      }),
    );
};

export const useStopShabondisAction = () => {
  const dispatch = useDispatch();
  const workspaceGroup = hooks.useWorkspaceGroup();
  return useCallback(
    (workspaceName) =>
      new Promise((resolve, reject) =>
        dispatch(
          actions.stopShabondis.trigger({
            values: {
              workspaceKey: { name: workspaceName, group: workspaceGroup },
            },
            resolve,
            reject,
          }),
        ),
      ),
    [dispatch, workspaceGroup],
  );
};

export const useDeleteShabondiAction = () => {
  const dispatch = useDispatch();
  const group = useShabondiGroup();
  return (params, options) =>
    dispatch(
      actions.deleteShabondi.trigger({
        params: { ...params, group },
        options,
      }),
    );
};

export const useDeleteShabondisInWorkspaceAction = () => {
  const dispatch = useDispatch();
  const workspaceKey = hooks.useWorkspaceKey();
  return useCallback(
    () =>
      new Promise((resolve, reject) =>
        dispatch(
          actions.deleteShabondis.trigger({
            values: { workspaceKey },
            resolve,
            reject,
          }),
        ),
      ),
    [dispatch, workspaceKey],
  );
};

export const useFetchShabondisAction = () => {
  const dispatch = useDispatch();
  return () => dispatch(actions.fetchShabondis.trigger());
};

export const useIsShabondiLoaded = () => {
  const mapState = useCallback((state) => !!state.ui.shabondi?.lastUpdated, []);
  return useSelector(mapState);
};

export const useIsShabondiLoading = () => {
  const mapState = useCallback((state) => !!state.ui.shabondi?.loading, []);
  return useSelector(mapState);
};

export const useShabondiGroup = () => {
  const pipelineGroup = hooks.usePipelineGroup();
  const pipelineName = hooks.usePipelineName();
  if (pipelineGroup && pipelineName)
    return hashByGroupAndName(pipelineGroup, pipelineName);
};

export const useShabondis = () => {
  const isAppReady = hooks.useIsAppReady();
  const workerId = hooks.useWorkerId();
  const group = useShabondiGroup();
  const isShabondiLoaded = useIsShabondiLoaded();
  const isShabondiLoading = useIsShabondiLoading();
  const fetchShabondis = useFetchShabondisAction();

  useEffect(() => {
    if (isShabondiLoaded || isShabondiLoading || !isAppReady) return;
    fetchShabondis();
  }, [fetchShabondis, isAppReady, isShabondiLoaded, isShabondiLoading]);

  return useSelector((state) => {
    const shabondis = selectors.getShabondisByGroup(state, { group });
    const results = shabondis.map((shabondi) => {
      const info = selectors.getInfoById(state, { id: workerId });
      const settingDefinitions =
        info?.classInfos.find(
          (def) => def.className === shabondi.shabondi__class,
        )?.settingDefinitions || [];
      return merge(shabondi, { settingDefinitions });
    });
    return results;
  });
};
