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
import PropTypes from 'prop-types';
import IconButton from '@material-ui/core/IconButton';
import Tooltip from '@material-ui/core/Tooltip';
import { Link } from 'react-router-dom';
import { get, isUndefined, isEmpty } from 'lodash';

import * as MESSAGES from 'constants/messages';
import * as utils from '../WorkspacesDetailPageUtils';
import TopicNewModal from './TopicNewModal';
import { DeleteDialog } from 'components/common/Mui/Dialog';
import { Main, NewButton, ActionIcon } from './styles';
import { SortTable } from 'components/common/Mui/Table';
import { useSetState } from 'utils/hooks';
import * as useApi from 'components/controller';
import * as URL from 'components/controller/url';
import useSnackbar from 'components/context/Snackbar/useSnackbar';

const Topics = props => {
  const { worker } = props;

  const { showMessage } = useSnackbar();
  const {
    data: topicsRes,
    isLoading: fetchingTopics,
    refetch,
  } = useApi.useFetchApi(`${URL.TOPIC_URL}`);
  const {
    data: pipelinesResponse,
    isLoading: fetchingPipelines,
  } = useApi.useFetchApi(`${URL.PIPELINE_URL}`);

  const { putApi: stopTopic } = useApi.usePutApi(`${URL.TOPIC_URL}`);
  const {
    getData: deleteTopicRes,
    deleteApi: deleteTopic,
  } = useApi.useDeleteApi(`${URL.TOPIC_URL}`);
  const { waitApi } = useApi.useWaitApi();

  const topics = get(topicsRes, 'data.result', []).filter(
    topic => topic.group === `${worker.settings.name}`,
  );

  const headRows = [
    { id: 'name', label: 'Topic name' },
    { id: 'partitions', label: 'Partitions' },
    { id: 'replication', label: 'Replication factor' },
    { id: 'metrics', label: 'Metrics (BytesInPerSec)' },
    { id: 'lastModified', label: 'Last modified' },
    { id: 'usedby', label: 'Used by pipeline' },
    { id: 'action', label: 'Action' },
  ];

  const actionButton = (name, isDisabled) => {
    const tooltipText = isDisabled
      ? `You cannot delete a pipeline while it's used in a pipeline`
      : `Delete ${name}`;

    return (
      <Tooltip title={tooltipText} enterDelay={1000}>
        <div>
          <IconButton
            aria-label="Edit"
            data-testid={name}
            disabled={isDisabled}
            onClick={() =>
              setState({
                isDeleteModalOpen: true,
                topicToBeDeleted: name,
              })
            }
          >
            <ActionIcon className="fas fa-trash-alt" />
          </IconButton>
        </div>
      </Tooltip>
    );
  };

  const getUsedByPipeline = (pipelines, topicName, workerClusterName) => {
    let usedByWhichPipeline = '';

    if (!isEmpty(pipelines)) {
      const targetPipeline = pipelines
        .filter(
          pipeline => pipeline.tags.workerClusterName === workerClusterName,
        )
        .find(pipeline =>
          pipeline.objects.some(object => object.name === topicName),
        );

      if (targetPipeline) {
        const { name } = targetPipeline;
        usedByWhichPipeline = (
          <Tooltip title={`Go to pipeline: ${name}`} enterDelay={1000}>
            <Link to={`/pipelines/edit/${worker.settings.name}/${name}`}>
              {name}
            </Link>
          </Tooltip>
        );
      }
    }

    return usedByWhichPipeline;
  };

  const rows = topics.map(topic => {
    const pipelines = get(pipelinesResponse, 'data.result', []);
    const usedByWhichPipeline = getUsedByPipeline(
      pipelines,
      topic.name,
      worker.settings.name,
    );

    const {
      name,
      numberOfPartitions,
      numberOfReplications,
      metrics,
      lastModified,
    } = topic;
    return {
      name,
      partitions: numberOfPartitions,
      replication: numberOfReplications,
      metrics: utils.getMetrics(metrics),
      lastModified: utils.getDateFromTimestamp(lastModified),
      usedby: usedByWhichPipeline,
      action: actionButton(name, Boolean(usedByWhichPipeline)),
    };
  });

  const [state, setState] = useSetState({
    deleting: false,
    isNewModalOpen: false,
    isDeleteModalOpen: false,
    topicToBeDeleted: '',
  });

  const handleDelete = async () => {
    setState({ deleting: true });
    const { topicToBeDeleted } = state;
    const checkFn = res => {
      return isUndefined(get(res, 'data.result.state', undefined));
    };
    const topicParams = {
      url: `${URL.TOPIC_URL}/${topicToBeDeleted}?group=${worker.settings.name}`,
      checkFn,
    };
    await stopTopic(`/${topicToBeDeleted}/stop?group=${worker.settings.name}`);
    await waitApi(topicParams);
    await deleteTopic(`${topicToBeDeleted}?group=${worker.settings.name}`);
    const isSuccess = get(deleteTopicRes(), 'data.isSuccess', false);
    setState({ deleting: false });

    if (isSuccess) {
      showMessage(`${MESSAGES.TOPIC_DELETION_SUCCESS} ${topicToBeDeleted}`);

      setState({
        isDeleteModalOpen: false,
        topicToBeDeleted: '',
      });
      refetch(true);
    }
  };

  const {
    isNewModalOpen,
    isDeleteModalOpen,
    deleting,
    topicToBeDeleted,
  } = state;

  return (
    <>
      <NewButton
        text="NEW TOPIC"
        data-testid="new-topic"
        onClick={() => {
          setState({ isNewModalOpen: true });
        }}
      />

      <Main>
        <SortTable
          isLoading={fetchingTopics || fetchingPipelines}
          headRows={headRows}
          rows={rows}
          tableName="topic"
        />
      </Main>

      <TopicNewModal
        isActive={isNewModalOpen}
        onClose={() => {
          setState({ isNewModalOpen: false });
        }}
        onConfirm={() => {
          refetch(true);
        }}
        worker={worker}
      />

      <DeleteDialog
        title="Delete topic?"
        content={`Are you sure you want to delete the topic: ${topicToBeDeleted} ? This action cannot be undone!`}
        open={isDeleteModalOpen}
        handleClose={() => setState({ isDeleteModalOpen: false })}
        handleConfirm={handleDelete}
        working={deleting}
        testId="delete-topic-dialog"
      />
    </>
  );
};

Topics.propTypes = {
  match: PropTypes.shape({
    url: PropTypes.string.isRequired,
  }).isRequired,
  worker: PropTypes.shape({
    settings: PropTypes.shape({
      name: PropTypes.string.isRequired,
      brokerClusterKey: PropTypes.shape({
        group: PropTypes.string.isRequired,
        name: PropTypes.string.isRequired,
      }).isRequired,
    }),
  }).isRequired,
};

export default Topics;
