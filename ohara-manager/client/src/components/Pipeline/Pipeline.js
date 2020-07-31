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

import React, { useEffect, useRef, createContext } from 'react';
import _ from 'lodash';

import * as context from 'context';
import * as hooks from 'hooks';
import * as pipelineUtils from './PipelineApiHelper';
import Paper from './Paper';
import Toolbar from './Toolbar';
import Toolbox from './Toolbox';
import PipelinePropertyView from './PipelinePropertyView';
import PipelinePropertyDialog from './PipelinePropertyDialog';
import { usePrevious } from 'utils/hooks';
import { PaperWrapper, StyledSplitPane } from './PipelineStyles';
import { usePipelineState as usePipelineReducerState } from './PipelineHooks';
import { CONNECTION_TYPE } from './PipelineApiHelper';
import { KIND, CELL_STATUS } from 'const';
import { DeleteDialog } from 'components/common/Dialog';

/* eslint-disable no-unused-expressions */

export const PaperContext = createContext(null);
export const PipelineStateContext = createContext(null);
export const PipelineDispatchContext = createContext(null);

const Pipeline = React.forwardRef((props, ref) => {
  const currentWorkspace = hooks.useWorkspace();
  const currentPipeline = hooks.usePipeline();
  const setSelectedCell = hooks.useSetSelectedCellAction();
  const startUpdateMetrics = hooks.useStartUpdateMetricsAction();
  const selectedCell = hooks.useCurrentPipelineCell();
  const streams = hooks.useStreams();
  const isStreamLoaded = hooks.useIsStreamLoaded();
  const isConnectorLoaded = hooks.useIsConnectorLoaded();
  const connectors = [...hooks.useConnectors(), ...hooks.useShabondis()];

  const {
    open: openPropertyDialog,
    isOpen: isPropertyDialogOpen,
    close: closePropertyDialog,
    data: propertyDialogData,
  } = context.usePipelinePropertyDialog();

  const [pipelineState, pipelineDispatch] = usePipelineReducerState();

  const {
    create: createConnector,
    update: updateConnector,
    start: startConnector,
    stop: stopConnector,
    remove: removeConnector,
    updateLink: updateLinkConnector,
    removeSourceLink: removeSourceLinkConnector,
    removeSinkLink: removeSinkLinkConnector,
  } = pipelineUtils.connector();

  const {
    create: createStream,
    update: updateStream,
    updateLinkTo: updateStreamLinkTo,
    updateLinkFrom: updateStreamLinkFrom,
    start: startStream,
    stop: stopStream,
    remove: removeStream,
    removeLinkTo: removeStreamLinkTo,
    removeLinkFrom: removeStreamLinkFrom,
  } = pipelineUtils.stream();

  const {
    createAndStart: createAndStartTopic,
    stopAndRemove: stopAndRemoveTopic,
  } = pipelineUtils.topic();

  const { updateCells, getUpdatedCells } = pipelineUtils.pipeline();
  const isInitialized = React.useRef(false);
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);
  const [currentCellData, setCurrentCellData] = React.useState(null);
  const paperApiRef = useRef(null);
  const forceDeleteList = React.useRef({
    connectors: [],
    fromStreams: [],
    toStreams: [],
  });

  const resetDeleteList = () =>
    (forceDeleteList.current = {
      connectors: [],
      fromStreams: [],
      toStreams: [],
    });

  const handleDialogClose = () => {
    setIsDeleteDialogOpen(false);
    resetDeleteList();
  };

  const clean = (cells, paperApi, onlyRemoveLink = false) => {
    Object.keys(cells).forEach((key) => {
      switch (key) {
        case 'source':
          removeSourceLinkConnector({ ...cells[key] }, cells.topic, paperApi);
          break;
        case 'sink':
          removeSinkLinkConnector({ ...cells[key] }, cells.topic, paperApi);
          break;
        case 'to':
          removeStreamLinkTo({ ...cells[key] }, cells.topic, paperApi);
          break;
        case 'from':
          removeStreamLinkFrom({ ...cells[key] }, cells.topic, paperApi);
          break;
        case 'topic':
          if (onlyRemoveLink) break;
          stopAndRemoveTopic({ ...cells[key] }, paperApi);
          break;

        default:
          break;
      }
    });
  };

  const prevWorkspace = usePrevious(currentWorkspace);
  const prevPipeline = usePrevious(currentPipeline);
  useEffect(() => {
    if (
      prevWorkspace?.name === currentWorkspace?.name &&
      prevPipeline?.name === currentPipeline?.name
    )
      return;

    // Allow the Paper to reload again
    isInitialized.current = false;

    // reset Toolbox since pipeline was changed
    pipelineDispatch({ type: 'resetToolbox' });
  }, [
    currentPipeline,
    currentWorkspace,
    pipelineDispatch,
    prevPipeline,
    prevWorkspace,
  ]);

  // When users create a new workspace and there has no pipeline yet
  // we need to clean up the Paper state from previous workspace
  useEffect(() => {
    if (currentPipeline?.name) return;
    if (!paperApiRef.current) return;

    paperApiRef.current.clearGraph({ skipGraphEvents: true });
  }, [currentPipeline]);

  useEffect(() => {
    if (!currentPipeline?.name) return;
    if (!pipelineState.isMetricsOn) return;

    startUpdateMetrics(currentPipeline?.name, {
      updatePipelineMetrics: (objects) => {
        // Updating Paper elements metrics
        paperApiRef.current.updateMetrics(objects);

        // Update Property view metrics panel
        pipelineDispatch({ type: 'updateMetrics', payload: objects });
      },
    });
  }, [
    currentPipeline,
    pipelineDispatch,
    pipelineState.isMetricsOn,
    startUpdateMetrics,
  ]);

  // Only run this once since we only want to load the graph once and maintain
  // the local state ever since
  useEffect(() => {
    if (isInitialized.current) return;
    if (!paperApiRef.current) return;
    if (!isConnectorLoaded) return;
    if (!isStreamLoaded) return;
    if (!currentPipeline) return;

    paperApiRef.current.loadGraph(getUpdatedCells(currentPipeline));
    isInitialized.current = true;
  }, [currentPipeline, getUpdatedCells, isConnectorLoaded, isStreamLoaded]);

  const handleSubmit = (params, values, paperApi) => {
    const { cell, topics = [] } = params;
    const { kind } = cell;
    switch (kind) {
      case KIND.source:
      case KIND.sink:
        updateConnector(cell, topics, values, connectors, paperApi);
        break;
      case KIND.stream:
        updateStream(cell, topics, values, streams, paperApi);
        break;
      default:
        break;
    }
  };

  // Deleting a topic involves some different logic from deleting a connector
  // We need to ensure it doesn't have any links that link to a running element as we need to update
  // the connected element's topicKeys and this update is only allowed when an element is not in running/failed state
  const deleteTopic = (forceDeleteList, resetDeleteList, paperApi) => {
    const { connectors, toStreams, fromStreams, topic } = forceDeleteList;
    // Remove all links from the target topic
    connectors.forEach((connector) => {
      const connectorCell = paperApi.getCell(connector.name);
      const kind = connectorCell?.kind;
      if (kind) clean({ [kind]: connectorCell, paperApi });
    });

    toStreams.forEach((stream) => {
      const streamCell = paperApi.getCell(stream.name);
      clean({ to: streamCell, paperApi });
    });

    fromStreams.forEach((stream) => {
      const streamCell = paperApi.getCell(stream.name);
      clean({ from: streamCell, paperApi });
    });

    stopAndRemoveTopic(topic, paperApi);

    // Reset the list
    resetDeleteList();
  };

  const handleCellSelect = (element) => {
    setSelectedCell(element);
  };

  const handleCellDeselect = () => {
    setSelectedCell(null);
  };

  const handleChange = _.debounce((paperApi) => updateCells(paperApi), 1000);

  const handleElementDelete = () => {
    const paperApi = paperApiRef.current;
    const { kind } = currentCellData;

    if (kind === KIND.topic) {
      deleteTopic(forceDeleteList.current, resetDeleteList, paperApi);
    } else if (kind === KIND.source || kind === KIND.sink) {
      removeConnector(currentCellData, paperApi);
    } else if (kind === KIND.stream) {
      removeStream(currentCellData, paperApi);
    }

    setIsDeleteDialogOpen(false);
  };

  const getCurrentCellName = (cellData) => {
    if (cellData === null) return '';
    if (cellData.kind !== KIND.topic) return cellData.name;
    return cellData.isShared ? cellData.name : cellData.displayName;
  };

  const handleElementAdd = (cellData, paperApi) => {
    const { kind, isTemporary } = cellData;
    switch (kind) {
      case KIND.sink:
      case KIND.source:
        if (!isTemporary) {
          createConnector(cellData, paperApi);
        }
        break;

      case KIND.stream:
        if (!isTemporary) {
          createStream(cellData, paperApi);
        }
        break;

      case KIND.topic:
        createAndStartTopic(cellData, paperApi);
        break;

      default:
        break;
    }
  };

  const handleCellConfigClick = (cellData, paperApi) => {
    const { displayName, kind, name } = cellData;
    let targetCell;
    switch (kind) {
      case KIND.source:
      case KIND.sink:
        targetCell = connectors.find((connector) => connector.name === name);
        break;
      case KIND.stream:
        targetCell = streams.find((stream) => stream.name === name);
        break;
      default:
        break;
    }

    openPropertyDialog({
      title: `Edit the property of ${displayName}`,
      classInfo: targetCell,
      cellData,
      paperApi,
    });
  };

  const handleConnect = (cells, paperApi) => {
    const {
      type,
      source,
      toStream,
      fromStream,
      sink,
      link,
      topic,
      firstLink,
      secondeLink,
    } = pipelineUtils.utils.getConnectionOrder(cells);
    switch (type) {
      case CONNECTION_TYPE.SOURCE_TOPIC:
        updateLinkConnector({ connector: source, topic, link }, paperApi);
        break;
      case CONNECTION_TYPE.TOPIC_SINK:
        updateLinkConnector({ connector: sink, topic, link }, paperApi);
        break;
      case CONNECTION_TYPE.STREAM_TOPIC:
        updateStreamLinkTo({ toStream, topic, link }, paperApi);
        break;
      case CONNECTION_TYPE.TOPIC_STREAM:
        updateStreamLinkFrom({ fromStream, topic, link }, paperApi);
        break;
      case CONNECTION_TYPE.SOURCE_TOPIC_SINK:
        createAndStartTopic(
          ({
            id: topic.id,
            name: topic.name,
            className: topic.className,
          } = topic),
          paperApi,
        ).then(() => {
          updateLinkConnector(
            { connector: source, topic, link: firstLink },
            paperApi,
          );
          updateLinkConnector(
            { connector: sink, topic, link: secondeLink },
            paperApi,
          );
        });

        break;
      case CONNECTION_TYPE.SOURCE_TOPIC_STREAM:
        createAndStartTopic(
          ({
            id: topic.id,
            name: topic.name,
            className: topic.className,
          } = topic),
          paperApi,
        ).then(() => {
          updateLinkConnector(
            { connector: source, topic, link: firstLink },
            paperApi,
          );
          updateStreamLinkFrom({ fromStream, topic, link }, paperApi);
        });

        break;
      case CONNECTION_TYPE.STREAM_TOPIC_SINK:
        createAndStartTopic(
          ({
            id: topic.id,
            name: topic.name,
            className: topic.className,
          } = topic),
          paperApi,
        ).then(() => {
          updateStreamLinkTo({ toStream, topic, link }, paperApi);
          updateLinkConnector(
            { connector: sink, topic, link: secondeLink },
            paperApi,
          );
        });

        break;
      case CONNECTION_TYPE.STREAM_TOPIC_STREAM:
        createAndStartTopic(
          ({
            id: topic.id,
            name: topic.name,
            className: topic.className,
          } = topic),
          paperApi,
        ).then(() => {
          updateStreamLinkTo({ toStream, topic, link }, paperApi);
          updateStreamLinkFrom({ fromStream, topic, link }, paperApi);
        });

        break;
      default:
        break;
    }
  };

  const handleDisConnect = (cells, paperApi) => {
    const {
      type,
      source,
      toStream,
      fromStream,
      sink,
      topic,
    } = pipelineUtils.utils.getConnectionOrder(cells);
    switch (type) {
      case CONNECTION_TYPE.SOURCE_TOPIC:
        clean({ source, topic }, paperApi, true);
        break;
      case CONNECTION_TYPE.STREAM_TOPIC:
        clean({ to: toStream, topic }, paperApi, true);
        break;
      case CONNECTION_TYPE.TOPIC_STREAM:
        clean({ from: fromStream, topic }, paperApi, true);
        break;
      case CONNECTION_TYPE.TOPIC_SINK:
        clean({ sink, topic }, paperApi, true);
        break;
      default:
        break;
    }
  };

  const handleCellStartClick = (cellData, paperApi) => {
    switch (cellData.kind) {
      case KIND.sink:
      case KIND.source:
        startConnector(cellData, paperApi);
        break;

      case KIND.stream:
        startStream(cellData, paperApi);
        break;

      default:
        break;
    }
  };

  const handleCellStopClick = (cellData, paperApi) => {
    switch (cellData.kind) {
      case KIND.sink:
      case KIND.source:
        stopConnector(cellData, paperApi);
        break;

      case KIND.stream:
        stopStream(cellData, paperApi);
        break;

      default:
        break;
    }
  };

  const handleCellRemove = (cellData) => {
    if (cellData.kind === KIND.topic) {
      const topicConnectors = connectors.filter((connector) => {
        const topics =
          _.get(connector, 'topicKeys') ||
          _.get(connector, 'shabondi__source__toTopics') ||
          _.get(connector, 'shabondi__sink__fromTopics') ||
          [];
        return topics.find((topic) => topic.name === cellData.name);
      });
      const topicToStream = streams.filter((stream) =>
        stream.to.find((topic) => topic.name === cellData.name),
      );
      const topicFromStream = streams.filter((stream) =>
        stream.from.find((topic) => topic.name === cellData.name),
      );

      forceDeleteList.current = {
        connectors: topicConnectors,
        toStreams: topicToStream,
        fromStreams: topicFromStream,
        topic: cellData,
      };
    }

    setIsDeleteDialogOpen(true);
    setCurrentCellData(cellData);
  };

  const getRunningElements = () => {
    const { connectors, toStreams, fromStreams } = forceDeleteList.current;
    return connectors
      .concat(toStreams, fromStreams)
      .filter(
        (element) => element?.state?.toLowerCase() === CELL_STATUS.running,
      );
  };

  const renderDeleteDialogContent = ({
    getCurrentCellName,
    currentCellData,
    getRunningElements,
  }) => {
    const runningElements = getRunningElements();
    if (runningElements.length > 0) {
      return `Before deleting this topic, you must stop the pipeline components link to it: ${runningElements
        .map((element) => element?.name)
        .join(', ')}`;
    }

    return `Are you sure you want to delete the element: ${getCurrentCellName(
      currentCellData,
    )} ? This action cannot be undone!`;
  };

  // Public APIs
  React.useImperativeHandle(ref, () => {
    if (!paperApiRef.current) return null;
    const paperApi = paperApiRef.current;

    return {
      highlight(id) {
        paperApi.highlight(id);
        const selectedCell = paperApi.getCell(id);
        setSelectedCell(selectedCell);
      },
    };
  });

  if (!currentWorkspace) return null;

  return (
    <PipelineDispatchContext.Provider value={pipelineDispatch}>
      <PipelineStateContext.Provider value={pipelineState}>
        <PaperContext.Provider value={{ ...paperApiRef.current }}>
          {paperApiRef.current && currentPipeline && (
            <Toolbar
              handleToolbarClick={(panel) => {
                const isExpanded = pipelineState.toolboxExpanded[panel];
                pipelineDispatch({ type: 'resetToolboxExpanded' });

                // Toggle the panel if it's already open
                if (!isExpanded) {
                  pipelineDispatch({
                    type: 'setToolbox',
                    payload: panel,
                  });
                }
              }}
              handleToolboxOpen={() =>
                pipelineDispatch({ type: 'openToolbox' })
              }
              isToolboxOpen={pipelineState.isToolboxOpen}
            />
          )}

          <PaperWrapper>
            {selectedCell && (
              <>
                <StyledSplitPane
                  defaultSize={320}
                  maxSize={500}
                  minSize={300}
                  primary="second"
                  split="vertical"
                >
                  <div></div>

                  <PipelinePropertyView
                    element={selectedCell}
                    handleClose={() => setSelectedCell(null)}
                    isMetricsOn={pipelineState.isMetricsOn}
                  />
                </StyledSplitPane>
              </>
            )}
            <Paper
              onCellConfig={handleCellConfigClick}
              onCellDeselect={handleCellDeselect}
              onCellRemove={handleCellRemove}
              onCellSelect={handleCellSelect}
              onCellStart={handleCellStartClick}
              onCellStop={handleCellStopClick}
              onChange={handleChange}
              onConnect={handleConnect}
              onDisconnect={handleDisConnect}
              onElementAdd={handleElementAdd}
              ref={paperApiRef}
            />
            {paperApiRef.current && currentPipeline && (
              <Toolbox
                expanded={pipelineState.toolboxExpanded}
                isOpen={pipelineState.isToolboxOpen}
                pipelineDispatch={pipelineDispatch}
                toolboxKey={pipelineState.toolboxKey}
              />
            )}
          </PaperWrapper>
          {paperApiRef.current && currentPipeline && (
            <>
              <PipelinePropertyDialog
                data={propertyDialogData}
                isOpen={isPropertyDialogOpen}
                onClose={closePropertyDialog}
                onSubmit={handleSubmit}
              />

              <DeleteDialog
                confirmDisabled={getRunningElements().length > 0}
                content={renderDeleteDialogContent({
                  getCurrentCellName,
                  currentCellData,
                  getRunningElements,
                })}
                maxWidth="xs"
                onClose={handleDialogClose}
                onConfirm={handleElementDelete}
                open={isDeleteDialogOpen}
                title="Delete the element"
              />
            </>
          )}
        </PaperContext.Provider>
      </PipelineStateContext.Provider>
    </PipelineDispatchContext.Provider>
  );
});

Pipeline.displayName = 'Pipeline';

export default Pipeline;
