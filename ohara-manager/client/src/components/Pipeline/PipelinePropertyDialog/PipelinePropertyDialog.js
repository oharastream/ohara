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

import { useRef, useState } from 'react';
import PropTypes from 'prop-types';
import _ from 'lodash';
import Button from '@material-ui/core/Button';
import Dialog from '@material-ui/core/Dialog';
import CloseIcon from '@material-ui/icons/Close';
import ListItem from '@material-ui/core/ListItem';
import Typography from '@material-ui/core/Typography';
import ListItemText from '@material-ui/core/ListItemText';
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import FilterListIcon from '@material-ui/icons/FilterList';
import InputAdornment from '@material-ui/core/InputAdornment';
import AccordionSummary from '@material-ui/core/AccordionSummary';
import AccordionDetails from '@material-ui/core/AccordionDetails';

import PipelinePropertyForm from './PipelinePropertyForm';
import { KIND } from 'const';
import * as hooks from 'hooks';
import { Type, Reference } from 'api/apiInterface/definitionInterface';
import PipelinePropertySpeedDial from './PipelinePropertySpeedDial';
import {
  StyleTitle,
  StyleIconButton,
  StyleMuiDialogContent,
  StyleMuiDialogActions,
  Sidebar,
  Content,
  StyleFilter,
  StyleAccordion,
} from './PipelinePropertyDialogStyles';

const PipelinePropertyDialog = (props) => {
  const { isOpen, onClose, onSubmit, data, maxWidth = 'md' } = props;
  const { title = '', classInfo = {}, cellData = { kind: '' }, paperApi } =
    data || {};
  const { kind } = cellData;
  const [expanded, setExpanded] = useState(null);
  const [selected, setSelected] = useState(null);
  const currentWorker = hooks.useWorker();
  const currentStreams = hooks.useStreams();
  const currentConnectors = [...hooks.useConnectors(), ...hooks.useShabondis()];
  const currentTopics = hooks.useTopicsInPipeline();
  const formRef = useRef(null);

  let targetCell;
  switch (kind) {
    case KIND.source:
    case KIND.sink:
      targetCell = currentConnectors.find(
        (connector) =>
          connector.className === classInfo.className &&
          connector.name === cellData.name,
      );
      break;
    case KIND.stream:
      targetCell = currentStreams.find(
        (stream) => stream.name === cellData.name,
      );
      break;

    default:
      break;
  }

  const definitionsByGroup = _(classInfo.settingDefinitions)
    // sort each definition by its orderInGroup property
    .sortBy((value) => value.orderInGroup)
    // we need to group the definitions by their group
    .groupBy((value) => value.group)
    // this is the tricky part...
    // we need to sort the object by keys
    // see https://github.com/lodash/lodash/issues/1459#issuecomment-253969771
    .toPairs()
    .sortBy(0)
    .fromPairs()
    .values()
    // finally, we only need the value of each variable in object
    .value();

  const getTopicWithKey = (values, key) => {
    if (_.isArray(values[key])) return;
    if (!values[key] || values[key] === 'Please select...') {
      values[key] = [];
      return;
    }

    const matchedTopic = _.find(
      currentTopics,
      (topic) => topic.displayName === values[key],
    );
    values[key] = [{ name: matchedTopic.name, group: matchedTopic.group }];
  };

  const isJsonString = (str) => {
    try {
      JSON.parse(str);
    } catch (e) {
      return false;
    }
    return true;
  };

  const handleSubmit = (values) => {
    const topicCells = paperApi.getCells(KIND.topic);
    let topics = [];
    values.settingDefinitions.forEach((def) => {
      if (
        def.valueType === Type.OBJECT_KEYS &&
        def.reference === Reference.TOPIC
      ) {
        if (def.key.length > 0) {
          getTopicWithKey(values, def.key);
          if (values[def.key].length === 0) {
            return;
          }
          topics.push({
            key: def.key,
            data: topicCells.find(
              (topic) => values[def.key][0].name === topic.name,
            ),
          });
        }
      }
      if (def.valueType === Type.TABLE) {
        if (
          Object.keys(values).includes(def.key) &&
          values[def.key].length > 0
        ) {
          const pickList = def.tableKeys.map((tableKey) => tableKey.name);
          values[def.key] = values[def.key].map((value) =>
            _.pick(value, pickList),
          );
        }
      }
      if (def.valueType === Type.TAGS && isJsonString(values[def.key])) {
        values[def.key] = JSON.parse(values[def.key]);
      }
    });
    topics = topics.filter((topic) => topic.data !== undefined);
    if (topics.length > 0) {
      onSubmit(
        {
          cell: cellData,
          topics,
        },
        values,
        paperApi,
      );
      onClose();
      return;
    }

    onSubmit({ cell: cellData }, values, paperApi);
    onClose();
  };

  const handleAccordionChange = (panel) => (event, isExpanded) => {
    setExpanded(isExpanded ? panel : false);
  };

  const DialogTitle = (params) => {
    const { title, onClose } = params;
    return (
      <StyleTitle disableTypography>
        <Typography variant="h4">{title}</Typography>
        {onClose && (
          <StyleIconButton data-testid="close-button" onClick={onClose}>
            <CloseIcon />
          </StyleIconButton>
        )}
      </StyleTitle>
    );
  };

  const handleClick = (key) => {
    setSelected(key);
    setTimeout(() => {
      if (formRef.current) formRef.current.scrollIntoView(key);
    }, 100);
  };

  return (
    <Dialog
      data-testid="property-dialog"
      fullWidth
      maxWidth={maxWidth}
      onClose={onClose}
      open={isOpen}
    >
      <DialogTitle onClose={onClose} title={title} />
      <StyleMuiDialogContent dividers>
        <Sidebar data-testid="sidebar">
          {
            // Unimplemented feature
            false && (
              <StyleFilter
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <FilterListIcon />
                    </InputAdornment>
                  ),
                }}
                placeholder="Quick filter"
                variant="outlined"
              />
            )
          }
          <div>
            {definitionsByGroup.map((group, index) => {
              const title = group[0].group;
              const defs = group.filter((def) => !def.internal);

              if (defs.length > 0) {
                return (
                  <StyleAccordion
                    data-testid="group-panel"
                    expanded={
                      expanded === title || (index === 0 && expanded === null)
                    }
                    key={title}
                    onChange={handleAccordionChange(title)}
                  >
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Typography>{_.capitalize(title)}</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                      <div>
                        {defs.map((def, index) => {
                          return (
                            <ListItem
                              button
                              className="nested"
                              key={def.key}
                              onClick={() => handleClick(def.key)}
                              selected={
                                def.key === selected ||
                                (selected === null && index === 0)
                              }
                            >
                              <ListItemText primary={def.displayName} />
                            </ListItem>
                          );
                        })}
                      </div>
                    </AccordionDetails>
                  </StyleAccordion>
                );
              } else {
                return null;
              }
            })}
          </div>
        </Sidebar>
        <Content data-testid="definition-content">
          <PipelinePropertyForm
            definitions={definitionsByGroup}
            freePorts={
              // only the connectors of worker need freePorts
              // we assign an empty array for RenderDefinition uses
              _.get(cellData, 'className', '').includes(KIND.shabondi)
                ? []
                : _.get(currentWorker, 'freePorts', [])
            }
            initialValues={targetCell}
            onSubmit={handleSubmit}
            ref={formRef}
            topics={currentTopics}
          />
        </Content>
        <div className="speed-dial">
          <PipelinePropertySpeedDial formRef={formRef} testId="autofill-dial" />
        </div>
      </StyleMuiDialogContent>
      <StyleMuiDialogActions>
        <Button
          autoFocus
          color="primary"
          onClick={() => formRef.current.submit()}
        >
          SAVE CHANGES
        </Button>
      </StyleMuiDialogActions>
    </Dialog>
  );
};

PipelinePropertyDialog.propTypes = {
  isOpen: PropTypes.bool.isRequired,
  data: PropTypes.object,
  maxWidth: PropTypes.string,
  onClose: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
};

export default PipelinePropertyDialog;
