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
import _ from 'lodash';
import $ from 'jquery';
import * as joint from 'jointjs';
import { useTheme } from '@material-ui/core/styles';

import { KIND, CELL_STATUS, CELL_TYPE } from 'const';
import { StyledPaper } from './PaperStyles';
import { createConnectorCell, createTopicCell, createLink } from './cell';
import { PipelineStateContext } from '../Pipeline';
import * as paperUtils from './PaperUtils';
import * as hooks from 'hooks';

const Paper = React.forwardRef((props, ref) => {
  const {
    onChange = _.noop,
    onCellSelect = _.noop,
    onCellDeselect = _.noop,
    onElementAdd = _.noop,
    onConnect = _.noop,
    onDisconnect = _.noop,

    // Cell events, these are passing down to cells
    onCellStart = _.noop,
    onCellStop = _.noop,
    onCellRemove = _.noop,
    onCellConfig = _.noop,
  } = props;

  const eventLog = hooks.useEventLog();

  const { palette } = useTheme();
  const { isMetricsOn } = React.useContext(PipelineStateContext);

  const graphRef = React.useRef(null);
  const paperRef = React.useRef(null);

  // Ensure that we only call the event handler once, this prevent paper from
  // making too many network requests to the backend service
  const cellAddRef = React.useRef(null);
  const cellChangeRef = React.useRef(null);
  const cellRemoveRef = React.useRef(null);
  // Prevent from getting stale event handlers
  const onCellEventRef = React.useRef(null);

  const [dragStartPosition, setDragStartPosition] = React.useState(null);

  React.useEffect(() => {
    const namespace = joint.shapes;
    graphRef.current = new joint.dia.Graph({}, { cellNamespace: namespace });

    paperRef.current = new joint.dia.Paper(
      {
        el: document.getElementById('paper'),
        model: graphRef.current,
        width: '100%',
        height: '100%',

        // Grid settings
        gridSize: 10,
        drawGrid: { name: 'dot', args: { color: palette.grey[300] } },
        background: { color: palette.common.white },

        // default highlighter, this is only used on LinkView
        highlighting: {
          default: {
            name: 'stroke',
            options: {
              padding: 4,
              rx: 4,
              ry: 4,
              attrs: {
                'stroke-width': 2,
                stroke: palette.action.active,
              },
            },
          },
        },

        // Ensures the link should always link to a valid target
        linkPinning: false,

        // Fixes es6 module issue with JointJS
        cellViewNamespace: joint.shapes,

        // prevent graph from stepping outside of the paper
        restrictTranslate: true,
      },
      { cellNamespace: namespace },
    );
  }, [
    palette.action.active,
    palette.common.white,
    palette.grey,
    palette.primary.main,
  ]);

  // This paperApi should be passed into all event handlers as the second
  // parameter,  // e.g., onConnect(eventObject, paperApi)
  // the only exception is the onRemove as it doesn't any other parameters.
  const paperApi = ref.current;

  React.useEffect(() => {
    onCellEventRef.current = {
      onElementAdd,
      onChange,
    };
  }, [onChange, onElementAdd]);

  React.useEffect(() => {
    const graph = graphRef.current;
    const paper = paperRef.current;

    paper.on('blank:pointerdown', (event, x, y) => {
      setDragStartPosition({
        x: x * paper.scale().sx,
        y: y * paper.scale().sy,
      });
    });

    paper.on('blank:pointermove', (event) => {
      // Prevent this event from interfering with Toolbox's drag and drop event
      // We will re-enable it in `pointerup` event
      $('.toolbox').css('pointer-events', 'none');
      if (dragStartPosition) {
        paper.translate(
          event.offsetX - dragStartPosition.x,
          event.offsetY - dragStartPosition.y,
        );
      }
    });

    paper.on('blank:pointerup', () => {
      setDragStartPosition(null);
      $('.toolbox').css('pointer-events', 'auto');
    });

    paper.on('blank:mouseover', () => {
      findElementViews().forEach(paperUtils.hideMenu);
    });

    paper.on('link:mouseenter', (linkView) => {
      const source = linkView.model.getSourceCell();
      const target = linkView.model.getTargetCell();
      const sourceStatus = source.get('status').toLowerCase();
      const targetStatus = target.get('status').toLowerCase();
      const hasRunningSource =
        source.get('kind') !== KIND.topic &&
        (sourceStatus === CELL_STATUS.running ||
          sourceStatus === CELL_STATUS.failed);

      const hasRunningTarget =
        target.get('kind') !== KIND.topic &&
        (targetStatus === CELL_STATUS.running ||
          targetStatus === CELL_STATUS.failed);

      const toolsView = new joint.dia.ToolsView({
        tools: [
          // Allow users to add vertices on link view
          new joint.linkTools.Vertices(),
          new joint.linkTools.Segments(),
          new joint.linkTools.TargetAnchor({
            restrictArea: true,
            areaPadding: 100,
          }),
          new joint.linkTools.Boundary(),
          // Add a custom remove tool
          new joint.linkTools.Remove({
            distance: '50%',
            markup: [
              {
                tagName: 'circle',
                selector: 'delete-paper-link-button',
                attributes: {
                  r: 8,
                  fill: 'grey',
                  cursor: 'pointer',
                },
              },
              {
                tagName: 'path',
                selector: 'icon',
                attributes: {
                  d: 'M -3 -3 3 3 M -3 3 3 -3',
                  fill: 'none',
                  stroke: palette.common.white,
                  'stroke-width': 2,
                  'pointer-events': 'none',
                },
              },
            ],
            action(event, linkView, toolView) {
              const source = linkView.model.getSourceElement();
              const target = linkView.model.getTargetElement();
              linkView.model.remove({ ui: true, tool: toolView.cid });

              onDisconnect(
                {
                  sourceElement: paperUtils.getCellData(source),
                  link: paperUtils.getCellData(linkView),
                  targetElement: paperUtils.getCellData(target),
                },
                paperApi,
              );
            },
          }),
        ],
      });

      // It's not allowed to update/remove the link if it has a connection
      if (!hasRunningSource && !hasRunningTarget) {
        linkView.addTools(toolsView);
      }

      linkView.highlight();
    });

    paper.on('link:mouseleave', (linkView) => {
      paper.removeTools();
      linkView.unhighlight();
    });

    paper.on('element:mouseenter', (elementView) => {
      // Make sure all menus are hidden, this prevents a bug where two connectors
      // are hovered and covered each other
      findElementViews().forEach(paperUtils.hideMenu);

      const hasHalfWayLink = graph
        .getLinks()
        .some((link) => !link.get('target').id);

      // Don't display the menu if the element is being connected, this prevents
      // users from accidentally click on these action buttons
      if (!hasHalfWayLink) {
        return paperUtils.showMenu(elementView);
      }

      elementView.hover();
    });

    paper.on('element:mouseleave', (elementView, event) => {
      const width = elementView.$box.width();
      const height = elementView.$box.height();
      const { left, top } = elementView.$box.offset();
      const { clientX, clientY } = event;

      // mouseenter and mouseleave events both get triggered again when hovering
      // on menu's buttons. This creates a flickering effect which looks a lot like a
      // UI defect. Here, we're calculating if the mouse is still moving inside the
      // paper element, if so. We don't fire the event again. This does fix the issue
      // but if user's mouse moves too fast, the bug could be appeared.
      const isOutSideOfTheElement =
        clientX > width + left ||
        clientY > height + top ||
        clientX < left ||
        clientY < top;

      if (isOutSideOfTheElement) paperUtils.hideMenu(elementView);
    });

    // Create a link that moves along with mouse cursor
    $(document).on('mousemove.createlink', (event) => {
      if (paperApi) {
        const link = graph.getLinks().find((link) => !link.get('target').id);

        if (link) {
          const localPoint = paperApi.getLocalPoint();
          const scale = paperApi.getScale();
          const { offsetLeft, offsetTop } = paperApi.getBbox();

          link.target({
            x: (event.pageX - offsetLeft) / scale.sx + localPoint.x,
            y: (event.pageY - offsetTop) / scale.sy + localPoint.y,
          });

          link.attr({
            // prevent the link from accidentally clicking by users while the link is still
            // a half way link. The `root` here is the SVG container element of
            // the link
            root: { style: 'pointer-events: none' },
            line: { stroke: palette.grey[500] },
          });
        }
      }
    });

    // Binding custom event handlers
    // Graph Events

    // OnChange event handler is called on graph's `add`, `change` and `remove` events
    graph.on('add', (cell, child, options = {}) => {
      if (!paperApi) return;
      if (_.isEqual(cellAddRef.current, cell)) return;
      // half-way link is not counted in the `add` event
      if (cell.isLink() && !cell.target().id) return;

      const { skipGraphEvents = false } = options;
      const data = paperUtils.getCellData(cell);

      if (!skipGraphEvents) {
        onCellEventRef.current.onElementAdd(data, paperApi);
        onCellEventRef.current.onChange(paperApi);
      }

      paperUtils.updateStatus(cell, paperApi);
      cellAddRef.current = cell;
    });

    graph.on('change', (cell, options = {}) => {
      if (!paperApi) return;
      if (_.isEqual(cellChangeRef.current, options)) return;
      if (cell.isLink() && !cell.target().id) return;

      const { skipGraphEvents = false } = options;

      paperUtils.updateStatus(cell, paperApi);
      if (!skipGraphEvents) {
        onCellEventRef.current.onChange(paperApi);
      }

      cellChangeRef.current = options;
    });

    graph.on('remove', (cell, child, options = {}) => {
      if (!paperApi) return;
      if (_.isEqual(cellRemoveRef.current, cell)) return;
      if (cell.isLink() && !cell.target().id) return;

      const { skipGraphEvents = false } = options;
      paperUtils.updateStatus(cell, paperApi);

      if (!skipGraphEvents) {
        onCellEventRef.current.onChange(paperApi);
      }

      cellRemoveRef.current = cell;
    });

    // Paper events
    paper.on('element:pointerclick', (elementView) => {
      resetElements();

      elementView
        .active()
        .hover()
        .showElement('menu')
        .hideElement('metrics')
        .hideElement('status')
        .setIsSelected(true);

      const sourceLink = graph
        .getLinks()
        .find((link) => !link.get('target').id);

      if (sourceLink) {
        const result = paperUtils.createConnection({
          sourceLink,
          targetElementView: elementView,
          eventLog,
          paperApi,

          // Since createConnection is using quite some JointJS APIs
          // and these APIs are not listed/wrapped in our public APIs
          // so we're passing graph down here
          graph,
        });

        if (result) {
          return onConnect(result, paperApi);
        }

        return resetLinks();
      }

      onCellSelect(paperUtils.getCellData(elementView), paperApi);
    });

    // Element button events, these are custom event binding down in the
    // element like connector or topic
    paper.on('element:link:button:pointerclick', (elementView) => {
      paperApi.addLink(elementView.model.get('id'));
    });

    paper.on('element:start:button:pointerclick', (elementView) => {
      onCellStart(paperUtils.getCellData(elementView), paperApi);
      isMetricsOn && elementView.toggleMetrics(true);
    });

    paper.on('element:stop:button:pointerclick', (elementView) => {
      onCellStop(paperUtils.getCellData(elementView), paperApi);
      elementView.toggleMetrics(false);
    });

    paper.on('element:config:button:pointerclick', (elementView) => {
      onCellConfig(paperUtils.getCellData(elementView), paperApi);
      paperUtils.hideMenu(elementView);
    });

    paper.on('element:remove:button:pointerclick', (elementView) => {
      onCellRemove(paperUtils.getCellData(elementView), paperApi);
    });

    paper.on('blank:pointerclick', () => {
      resetElements();
      resetLinks();
      onCellDeselect(paperApi);
    });

    function resetElements() {
      findElementViews().forEach((elementView) => {
        elementView
          .unActive()
          .unHover()
          .hideElement('menu')
          .setIsSelected(false);

        if (elementView.model.get('showMetrics')) {
          return elementView.showElement('metrics');
        }
        elementView.showElement('status');
      });
    }

    function resetLinks() {
      const links = graph.getLinks();
      if (links.length > 0) {
        // There should only be one
        const unConnectedLink = links.find((link) => !link.get('target').id);
        if (unConnectedLink) unConnectedLink.remove();
      }
    }

    return () => {
      // Event should all be cleaned in every render, or it will causes weird
      // behavior in UI

      $(document).off('mousemove.createlink');

      // Cell events
      paper.off('cell:pointermove');

      // Element events
      paper.off('element:pointerclick');
      paper.off('element:mouseenter');
      paper.off('element:mouseleave');
      paper.off('element:link:button:pointerclick');
      paper.off('element:start:button:pointerclick');
      paper.off('element:stop:button:pointerclick');
      paper.off('element:config:button:pointerclick');
      paper.off('element:remove:button:pointerclick');

      // Link
      paper.off('link:pointerclick');
      paper.off('link:mouseenter');
      paper.off('link:mouseleave');

      // Blank events
      paper.off('blank:pointerclick');
      paper.off('blank:pointerdown');
      paper.off('blank:pointerup');
      paper.off('blank:pointermove');
      paper.off('blank:mouseover');
    };
  }, [
    dragStartPosition,
    eventLog,
    isMetricsOn,
    onCellConfig,
    onCellDeselect,
    onCellRemove,
    onCellSelect,
    onCellStart,
    onCellStop,
    onConnect,
    onDisconnect,
    palette.common.white,
    palette.grey,
    paperApi,
  ]);

  React.useImperativeHandle(ref, () => {
    const graph = graphRef.current;
    const paper = paperRef.current;

    if (!graph || !paper) return;

    return {
      // Public APIs
      addElement(data, options) {
        const { source, sink, stream, topic } = KIND;
        const { kind } = data;

        const statusColors = {
          stopped: palette.text.secondary,
          pending: palette.warning.main,
          running: palette.success.main,
          failed: palette.error.main,
        };

        const newData = {
          ...data,
          statusColors,
          showMetrics: isMetricsOn,
          paperApi: ref.current,
        };

        let cell;
        if (kind === source || kind === sink || kind === stream) {
          cell = createConnectorCell(newData);
        } else if (kind === topic) {
          cell = createTopicCell(newData);
        } else {
          // invalid type won't be added thru this method (e.g., link)
          throw new Error(
            `paperApi: addElement(data: object) invalid kind from the given data object!`,
          );
        }

        graph.addCell(cell, options);
        const currentCell = graph.getLastCell();
        return paperUtils.getCellData(currentCell);
      },
      removeElement(id, options) {
        if (typeof id !== 'string') {
          throw new Error(
            `paperApi: removeElement(id: string) invalid argument id!`,
          );
        }

        const cell = findCell(id);

        if (!cell || !cell.isElement())
          throw new Error(
            `paperApi: removeElement(id: string) can only remove an element with this method`,
          );

        graph.removeCells(cell, options);
      },
      updateElement(id, data, options) {
        const elementView = findElementViews().find(
          (elementView) => elementView.model.id === id,
        );

        if (elementView) {
          elementView.updateElement(
            {
              ...elementView.model.attributes,
              ...data,
            },
            options,
          );

          // Update element status, topic has a slightly different logic, so we
          // handle it differently in each switch case
          const status = elementView.model.get('status').toLowerCase();
          const isTopic = elementView.model.get('kind') === KIND.topic;

          switch (status.toLowerCase()) {
            case CELL_STATUS.running:
              if (isTopic) {
                // Topic starts with pending state, and after it's successfully
                // created and started, we need to update its status again and so
                // we need to enable its menu here
                elementView.enableMenu();
              } else {
                elementView.disableMenu(['link', 'start', 'config', 'remove']);
                isMetricsOn && elementView.toggleMetrics(true);
              }
              break;
            case CELL_STATUS.failed:
              if (isTopic) {
                elementView.disableMenu(['link']);
              } else {
                elementView.disableMenu(['link', 'config', 'remove']);
                elementView.toggleMetrics(false);
              }

              break;
            case CELL_STATUS.stopped:
              elementView.enableMenu();

              if (!isTopic) {
                elementView.toggleMetrics(false);
              }

              // If a connector or stream doesn't have any connections yet,
              // disable the `start` and `stop` action buttons
              if (graph.getConnectedLinks(elementView.model).length === 0) {
                elementView.disableMenu(['start', 'stop']);
              } else {
                // For a source connector which has connection, the `link` action
                // button should be disabled
                const kind = elementView.model.get('kind');
                if (kind === KIND.source) {
                  elementView.disableMenu(['link']);
                }

                // Because stream could have more than one connection (in and out)
                // so we need to make sure if it's a sink (Element is a leaf by
                // JointJS' definition), then disable the `link` action button
                // if it is NOT a sink
                if (kind === KIND.stream && !graph.isSink(elementView.model)) {
                  elementView.disableMenu(['link']);
                }
              }

              break;
            case CELL_STATUS.pending:
              elementView.disableMenu();
              break;
            default:
              throw new Error(`Unknown cell status: ${status}`);
          }
        }
      },
      addLink(sourceId, targetId, data, options) {
        if (typeof sourceId !== 'string') {
          throw new Error(
            `paperApi: addLink(sourceId: string, targetId: string?) invalid argument sourceId`,
          );
        }

        const newLink = createLink({ sourceId, data });

        // TargetId is not supplied, meaning, the target is not decided yet,
        // so we will assign mouse position later on. This allows user to control
        // the link
        if (targetId === undefined) {
          // Hide the link when it's first added into Paper. This is because
          // the link position will be set to `0, 0`. We will wait until user
          // moves their cursor and start using the mouse position as link's
          // position
          newLink.attr({
            line: {
              stroke: 'transparent',
            },
          });
          return graph.addCell(newLink, options);
        }

        newLink.target({ id: targetId });
        graph.addCell(newLink, options);

        return paperUtils.getCellData(newLink);
      },
      removeLink(id, options) {
        if (typeof id !== 'string') {
          throw new Error(
            `paperApi: removeLink(id: string) invalid argument id`,
          );
        }

        graph.removeCells(graph.getCell(id), options);
      },
      getCell(id) {
        if (typeof id !== 'string') {
          throw new Error(`paperApi: getCell(id: string) invalid argument id`);
        }

        const result = findCell(id);

        if (result) return paperUtils.getCellData(result);
      },
      getCells(kind) {
        if (kind) {
          const validTypes = _.values(
            _.pick(KIND, ['source', 'sink', 'stream', 'topic']),
          );

          if (!validTypes.includes(kind))
            throw new Error(
              `paperApi: getCells(kind: string?) argument ${kind} is not a valid type! Available types are: ${validTypes.toString()}`,
            );

          return graph
            .getCells()
            .filter((cell) => cell.attributes.kind === kind)
            .map(paperUtils.getCellData);
        }

        return graph.getCells().map(paperUtils.getCellData);
      },
      loadGraph(cells) {
        // Clear the graph before loading
        graph.clear({ skipGraphEvents: true });

        // We currently are not saving these meta, and so when switching
        // pipeline these should be reset. In the future, we can load these
        // along with cell data from API
        paper.translate(0, 0);
        this.setScale(1);

        cells
          // Elements should be render first, and then the links
          .sort((a, b) => a.type.localeCompare(b.type))
          .forEach((cell) => {
            const { type, source, target, vertices } = cell;
            if (type === CELL_TYPE.ELEMENT) {
              return this.addElement(
                {
                  ...cell,
                  isSelected: false, // we don't want to recovery this state for now
                },
                {
                  skipGraphEvents: true,
                },
              );
            }

            if (type === CELL_TYPE.LINK) {
              this.addLink(
                source.id,
                target.id,
                {
                  vertices,
                },
                { skipGraphEvents: true },
              );
            }
          });

        // Disable running and failed elements action buttons
        findElementViews().forEach((elementView) =>
          this.updateElement(
            elementView.model.get('id'),
            paperUtils.getCellData(elementView),
          ),
        );
      },
      clearGraph(options) {
        graph.clear(options);
      },
      setScale(sx, sy) {
        if (sx === undefined) {
          throw new Error(
            'paperApi: setScale(sx: number, sy: number) argument sx is undefined!',
          );
        }

        if (sy === undefined) sy = sx;

        paper.scale(sx, sy);
      },
      getScale() {
        return paper.scale();
      },
      getBbox() {
        const { width, height } = paper.getComputedSize();
        const { left, top } = paper.$el.offset();

        return {
          offsetLeft: left,
          offsetTop: top,
          width,
          height,
        };
      },
      getLocalPoint() {
        return paper.paperToLocalPoint(paper.translate());
      },
      toJson() {
        return graph.toJSON();
      },
      fit() {
        paper.scaleContentToFit({
          padding: 30,
          maxScale: 1,
        });

        return this.getScale().sx;
      },
      center(id) {
        const elementView = findElementView(id);
        const elementBbox = {
          ...elementView.getBBox(),
          ...elementView.getBBox().center(),
        };

        const scale = this.getScale().sx;
        const contentLocalOrigin = paper.paperToLocalPoint(elementBbox);
        const { tx, ty } = paper.translate();
        const { width, height } = this.getBbox();
        const fittingBbox = { x: tx, y: ty, width, height };
        const origin = this.state.options.origin;
        const newOx = fittingBbox.x - contentLocalOrigin.x * scale - origin.x;
        const newOy = fittingBbox.y - contentLocalOrigin.y * scale - origin.y;

        paper.translate(
          // divide by 2 so it's centered
          newOx + fittingBbox.width / 2,
          newOy + fittingBbox.height / 2,
        );
      },
      toggleMetrics(isOpen) {
        findElementViews()
          .filter(
            ({ model }) =>
              model.get('kind') === KIND.source ||
              model.get('kind') === KIND.sink ||
              model.get('kind') === KIND.stream,
          )
          .forEach((element) => element.toggleMetrics(isOpen));
      },

      updateMetrics(objects) {
        objects
          .filter(({ kind }) => kind !== KIND.topic) // topic metrics are not displayed in the current UI
          .filter(({ state }) => state?.toLowerCase() === CELL_STATUS.running)
          .forEach((object) =>
            findElementView(object.name).updateMeters(object.nodeMetrics),
          );
      },

      highlight(id) {
        if (typeof id !== 'string') {
          throw new Error(
            `paperApi: highlight(id: string) invalid argument id`,
          );
        }

        const elementView = findElementView(id);
        if (elementView) {
          findElementViews().forEach((elementView) =>
            elementView.unActive().unHover().setIsSelected(false),
          );

          elementView.active().setIsSelected(true);
        }
      },

      state: {
        // Only expose necessary options from JointJS' default
        options: _.pick(paper.options, ['origin']),
      },
    };
  });

  // Private APIs
  function findElementViews() {
    const result = graphRef.current
      .getElements()
      .map((element) => paperRef.current.findViewByModel(element.get('id')));

    return result;
  }

  function findElementView(nameOrId) {
    return findElementViews().find(
      ({ model }) =>
        model.get('id') === nameOrId || model.get('name') === nameOrId,
    );
  }

  function findCell(nameOrId) {
    return (
      graphRef.current.getCell(nameOrId) ||
      graphRef.current.getCells().find((cell) => cell.get('name') === nameOrId)
    );
  }

  return (
    <StyledPaper className={`${dragStartPosition ? 'is-being-grabbed' : ''}`}>
      <div id="paper" />
    </StyledPaper>
  );
});

Paper.propTypes = {
  onChange: PropTypes.func.isRequired,
  onConnect: PropTypes.func.isRequired,
  onDisconnect: PropTypes.func.isRequired,
  onCellSelect: PropTypes.func.isRequired,
  onCellDeselect: PropTypes.func.isRequired,
  onElementAdd: PropTypes.func.isRequired,
  onCellStart: PropTypes.func.isRequired,
  onCellStop: PropTypes.func.isRequired,
  onCellRemove: PropTypes.func.isRequired,
  onCellConfig: PropTypes.func.isRequired,
};

export default Paper;
