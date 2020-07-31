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

import * as hooks from 'hooks';
import * as pipelineApiHelper from '../PipelineApiHelper';
import { PaperContext } from '../Pipeline';
import { KIND, CELL_STATUS, CELL_TYPE } from 'const';
import { usePrevious } from 'utils/hooks';

export const useRunningElements = () => {
  const paperApi = React.useContext(PaperContext);
  return paperApi
    .getCells()
    .filter((cell) => cell.kind !== KIND.topic)
    .filter((cell) => cell.cellType === CELL_TYPE.ELEMENT)
    .filter((cell) => cell.status?.toLowerCase() !== CELL_STATUS.stopped);
};

export const useRenderDeleteDialogContent = () => {
  const pipelineError = hooks.usePipelineError();
  const currentPipelineName = hooks.usePipelineName();
  const hasRunningElements = useRunningElements().length > 0;

  if (pipelineError) {
    return `Failed to delete services! You can try to delete again by hitting on the RETRY button`;
  }

  if (hasRunningElements) {
    return `Oops, there are still some running services in ${currentPipelineName}. You should stop them first and then you will be able to delete this pipeline.`;
  }

  return `Are you sure you want to delete ${currentPipelineName} ? This action cannot be undone!`;
};

export const useZoom = () => {
  const paperApi = React.useContext(PaperContext);
  const [paperScale, setPaperScale] = React.useState(
    () => paperApi.getScale().sx,
  );

  const currentPaperApi = usePrevious(paperApi);
  React.useEffect(() => {
    if (paperApi) setPaperScale(paperApi.getScale().sx);
  }, [currentPaperApi, paperApi]);

  const setZoom = (scale, instruction) => {
    const fixedScale = Number((Math.floor(scale * 100) / 100).toFixed(2));
    const allowedScales = [
      0.25,
      0.5,
      0.67,
      0.75,
      0.8,
      0.9,
      1.0,
      1.1,
      1.25,
      1.5,
    ];
    const isValidScale = allowedScales.includes(fixedScale);

    if (isValidScale) {
      // If the instruction is `fromDropdown`, we will use the scale it gives
      // and update the state right alway
      if (instruction === 'fromDropdown') {
        paperApi.setScale(scale);
        return setPaperScale(scale);
      }

      const scaleIndex = allowedScales.indexOf(fixedScale);
      let newScale;

      // in => zoom in
      // out => zoom out
      if (instruction === 'in') {
        newScale =
          scaleIndex >= allowedScales.length
            ? allowedScales[scaleIndex]
            : allowedScales[scaleIndex + 1];
      } else {
        newScale =
          scaleIndex <= 0
            ? allowedScales[scaleIndex]
            : allowedScales[scaleIndex - 1];
      }

      paperApi.setScale(newScale);
      return setPaperScale(newScale);
    }

    // Handle `none-valid` scales here
    const defaultScales = [0.5, 0.75, 1.0, 1.5];
    const closest = defaultScales.reduce((prev, curr) => {
      return Math.abs(curr - fixedScale) < Math.abs(prev - fixedScale)
        ? curr
        : prev;
    });

    let outScale;
    let inScale;
    if (closest === 0.5) {
      inScale = fixedScale <= 0.5 ? 0.5 : 0.75;
      outScale = 0.5;
    } else if (closest === 0.75) {
      inScale = fixedScale <= 0.75 ? 0.75 : 1;
      outScale = fixedScale >= 0.75 ? 0.75 : 0.5;
    } else if (closest === 1) {
      inScale = 1;
      outScale = 0.75;
    }

    const newScale = instruction === 'in' ? inScale : outScale;
    paperApi.setScale(newScale);
    setPaperScale(newScale);
    return newScale;
  };

  return {
    setZoom,
    scale: paperScale,
    setScale: setPaperScale,
  };
};

export const useMakeRequest = () => {
  const paperApi = React.useContext(PaperContext);
  const streamApiHelper = pipelineApiHelper.stream();
  const connectorApiHelper = pipelineApiHelper.connector();

  const makeRequest = (pipeline, action) => {
    const cells = paperApi.getCells();

    const connectors = cells.filter(
      (cell) => cell.kind === KIND.source || cell.kind === KIND.sink,
    );
    const streams = cells.filter((cell) => cell.kind === KIND.stream);

    let connectorPromises = [];
    let streamsPromises = [];

    if (action === 'start') {
      connectorPromises = connectors.map((cellData) =>
        connectorApiHelper.start(cellData, paperApi),
      );
      streamsPromises = streams.map((cellData) =>
        streamApiHelper.start(cellData, paperApi),
      );
    } else {
      connectorPromises = connectors.map((cellData) =>
        connectorApiHelper.stop(cellData, paperApi),
      );
      streamsPromises = streams.map((cellData) =>
        streamApiHelper.stop(cellData, paperApi),
      );
    }
    return Promise.all([...connectorPromises, ...streamsPromises]).then(
      (result) => result,
    );
  };

  return makeRequest;
};
