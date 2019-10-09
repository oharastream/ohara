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
import { cleanup, waitForElement, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/extend-expect';

import * as generate from 'utils/generate';
import * as useApi from 'components/controller';
import * as URL from 'components/controller/url';
import Topics from '../Topics';
import { renderWithProvider } from 'utils/testUtils';

jest.mock('components/controller');

afterEach(cleanup);

describe('<Topics />', () => {
  let props;
  let brokerClusterName;
  let topics;
  let pipelines;

  beforeEach(() => {
    props = {
      match: {
        url: generate.url(),
      },
      worker: {
        settings: {
          name: generate.name(),
          brokerClusterKey: {
            group: generate.name(),
            name: brokerClusterName,
          },
        },
      },
    };

    brokerClusterName = generate.serviceName();
    topics = generate.topics({
      brokerClusterName,
      workspaceName: props.worker.settings.name,
    });

    pipelines = [
      {
        name: generate.name(),
        status: 'Stopped',
        objects: [
          { kind: 'topic', name: topics[0].name },
          { kind: 'source', name: 'abc' },
        ],
        tags: { workerClusterName: props.worker.settings.name },
      },
    ];

    jest.spyOn(useApi, 'useFetchApi').mockImplementation(url => {
      if (url === `${URL.TOPIC_URL}`) {
        return {
          data: {
            data: {
              isSuccess: true,
              result: topics,
            },
          },
          isLoading: false,
        };
      }

      if (url === URL.PIPELINE_URL) {
        return {
          data: {
            data: {
              isSuccess: true,
              result: pipelines,
            },
          },
          isLoading: false,
        };
      }
    });

    jest.spyOn(useApi, 'useDeleteApi').mockImplementation(() => {
      return {
        getData: jest.fn(),
        deleteApi: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'usePostApi').mockImplementation(() => {
      return {
        getData: jest.fn(),
        postApi: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'usePutApi').mockImplementation(() => {
      return {
        putApi: jest.fn(),
      };
    });

    jest.spyOn(useApi, 'useWaitApi').mockImplementation(() => {
      return {
        waitApi: jest.fn(),
      };
    });
  });

  it('renders the page', async () => {
    await renderWithProvider(<Topics {...props} />);
  });

  it('should properly render the table data', async () => {
    const { getByTestId } = await renderWithProvider(<Topics {...props} />);

    const topicName = getByTestId('topic-name').textContent;
    const partitions = Number(getByTestId('topic-partitions').textContent);
    const replications = Number(getByTestId('topic-replication').textContent);
    const usedByPipeline = getByTestId('topic-usedby').textContent;
    const lastModified = getByTestId('topic-lastModified').textContent;

    expect(topicName).toBe(topics[0].name);
    expect(partitions).toBe(topics[0].numberOfPartitions);
    expect(replications).toBe(topics[0].numberOfReplications);
    expect(usedByPipeline).toBe(pipelines[0].name);

    // It's hard to assert the output date format since the topic last modified
    // date is generated. So we're asserting it with any given string here.
    expect(lastModified).toEqual(expect.any(String));
  });

  it('renders multiple topics', async () => {
    const topics = generate.topics({
      count: 5,
      brokerClusterName,
      workspaceName: props.worker.settings.name,
    });

    jest.spyOn(useApi, 'useFetchApi').mockImplementation(url => {
      if (url === `${URL.TOPIC_URL}`) {
        return {
          data: {
            data: {
              isSuccess: true,
              result: topics,
            },
          },
          isLoading: false,
        };
      }

      if (url === URL.PIPELINE_URL) {
        return {
          data: {
            data: {
              isSuccess: true,
              result: pipelines,
            },
          },
          isLoading: false,
        };
      }
    });

    const { getAllByTestId } = await renderWithProvider(<Topics {...props} />);

    const topicNames = await waitForElement(() => getAllByTestId('topic-name'));
    expect(topicNames.length).toBe(topics.length);
  });

  it('should close the new topic modal with cancel button', async () => {
    const { getByText, queryByTestId } = await renderWithProvider(
      <Topics {...props} />,
    );

    fireEvent.click(getByText('NEW TOPIC'));
    expect(queryByTestId('topic-new-modal')).toBeVisible();

    fireEvent.click(getByText('CANCEL'));
    expect(queryByTestId('topic-new-modal')).not.toBeVisible();
  });

  it('should close the delete dialog with the cancel button', async () => {
    pipelines = [
      {
        name: generate.name(),
        status: 'Stopped',
        objects: [],
        tags: {},
      },
    ];

    props = {
      match: {
        url: generate.url(),
      },
      worker: {
        settings: {
          name: generate.name(),
          brokerClusterKey: {
            name: generate.name(),
            brokerClusterName,
          },
        },
      },
    };

    topics = generate.topics({
      brokerClusterName,
      workspaceName: props.worker.settings.name,
    });

    jest.spyOn(useApi, 'useFetchApi').mockImplementation(url => {
      if (url === `${URL.TOPIC_URL}`) {
        return {
          data: {
            data: {
              isSuccess: true,
              result: topics,
            },
          },
          isLoading: false,
        };
      }

      if (url === URL.PIPELINE_URL) {
        return {
          data: {
            data: {
              isSuccess: true,
              result: pipelines,
            },
          },
          isLoading: false,
        };
      }
    });

    const { getByTestId, getByText } = await renderWithProvider(
      <Topics {...props} />,
    );

    const topic = getByTestId(topics[0].name);

    fireEvent.click(topic);
    expect(getByText('Delete topic?')).toBeVisible(); // Ensure the modal is opened by getting it's title

    fireEvent.click(getByText('CANCEL'));

    expect(getByText('Delete topic?')).not.toBeVisible();
  });
});
