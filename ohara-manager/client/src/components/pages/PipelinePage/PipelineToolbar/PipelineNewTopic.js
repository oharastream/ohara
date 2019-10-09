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

import React, {
  useState,
  useEffect,
  useImperativeHandle,
  forwardRef,
} from 'react';
import PropTypes from 'prop-types';
import DialogContent from '@material-ui/core/DialogContent';
import { isEmpty, get } from 'lodash';
import { Link } from 'react-router-dom';

import * as URLS from 'constants/urls';
import * as pipelineApi from 'api/pipelineApi';
import { Warning } from 'components/common/Messages';
import { createConnector } from './pipelineToolbarUtils';
import { graph as graphPropType } from 'propTypes/pipeline';
import { Select } from 'components/common/Mui/Form';

const PipelineNewTopic = forwardRef((props, ref) => {
  const [displayTopics, setDisplayTopics] = useState([]);
  const [disabledTopics, setDisabledTopics] = useState([]);
  const [currentTopic, setCurrentTopic] = useState('');

  const { enableAddButton, topics, workerClusterName } = props;

  useEffect(() => {
    const isDisabled =
      currentTopic === '' || currentTopic === 'Please select...';

    enableAddButton(isDisabled);

    const fetchPipelines = async () => {
      const response = await pipelineApi.fetchPipelines();
      const pipelines = get(response, 'data.result', []);

      const displayTopics = [];
      const disabledTopics = [];

      topics.forEach(topic => {
        const shouldBeDisabled = pipelines
          .filter(
            pipeline => pipeline.tags.workerClusterName === workerClusterName,
          )
          .some(pipeline => {
            return pipeline.objects.some(object => object.name === topic.name);
          });

        if (shouldBeDisabled) {
          disabledTopics.push(topic.name);
        }

        // We want to display all the topics
        displayTopics.push(topic.name);
      });

      setDisplayTopics(displayTopics);
      setDisabledTopics(disabledTopics);
    };

    fetchPipelines();
  }, [currentTopic, enableAddButton, topics, workerClusterName]);

  useImperativeHandle(ref, () => ({
    update() {
      const { updateGraph, pipelineName } = props;
      const activeTopic = topics.find(
        topic => topic.settings.name === currentTopic,
      );

      const connector = {
        ...activeTopic,
        className: 'topic',
        typeName: 'topic',
      };

      createConnector({ updateGraph, connector, pipelineName });
    },
  }));

  return (
    <>
      {isEmpty(topics) ? (
        <DialogContent>
          <Warning
            text={
              <>
                {`You don't have any topics available in this workspace yet. But you can create one in `}
                <Link to={`${URLS.WORKSPACES}/${workerClusterName}/topics`}>
                  here
                </Link>
              </>
            }
          />
        </DialogContent>
      ) : (
        <DialogContent>
          <Select
            required
            autoFocus
            input={{
              name: 'topic',
              onChange: event => setCurrentTopic(event.target.value),
              value: currentTopic,
            }}
            list={displayTopics}
            disables={disabledTopics}
            inputProps={{
              'data-testid': 'topic-select',
            }}
          />
        </DialogContent>
      )}
    </>
  );
});

PipelineNewTopic.propTypes = {
  graph: PropTypes.arrayOf(graphPropType).isRequired,
  updateGraph: PropTypes.func.isRequired,
  topics: PropTypes.array.isRequired,
  enableAddButton: PropTypes.func.isRequired,
  workerClusterName: PropTypes.string.isRequired,
  pipelineName: PropTypes.string.isRequired,
};

export default PipelineNewTopic;
