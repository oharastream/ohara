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

import React, { useState, useEffect } from 'react';
import FormControl from '@material-ui/core/FormControl';
import { isEmpty, isUndefined, isNull, get } from 'lodash';
import { Field } from 'react-final-form';

import * as connectorApi from 'api/connectorApi';
import * as pipelineApi from 'api/pipelineApi';
import * as MESSAGES from 'constants/messages';
import ColumnTable from './ColumnTable';
import Tabs from './Tabs';
import useSnackbar from 'components/context/Snackbar/useSnackbar';
import { CONNECTOR_ACTIONS } from 'constants/pipelines';
import { validateConnector } from 'api/validateApi';
import { InputField, Select, Checkbox } from 'components/common/Mui/Form';
import { findByGraphName } from '../pipelineUtils';

export const getCurrTopicId = ({ originals, target = '' }) => {
  if (isEmpty(originals) || isNull(target) || target === 'Please select...')
    return [];

  const findByTopicName = ({ name }) => name === target;
  const { name } = originals.find(findByTopicName);

  return name;
};

export const getUpdatedTopic = ({
  graph,
  connectorName,
  currTopicName,
  originalTopics,
}) => {
  const connector = findByGraphName(graph, connectorName);
  let updatedTopic;

  if (connector.kind === 'source') {
    const findByTopicName = topic => topic.name === currTopicName;
    const currTopic = originalTopics.find(findByTopicName);
    const topicName = isUndefined(currTopic) ? [] : [currTopic.name];
    updatedTopic = {
      update: { ...connector, name: connectorName, to: topicName },
    };
  } else {
    const currSink = findByGraphName(graph, connectorName);
    const findByCurrTopicName = g => g.name === currTopicName;
    const topic = graph.find(findByCurrTopicName);

    // Extra props for sink connector to properly render
    const sinkProps = {
      isFromTopic: true,
      updatedName: connectorName,
      sinkName: connectorName,
    };

    if (topic) {
      const to = [...new Set([...topic.to, connectorName])];
      updatedTopic = { sinkProps, update: { ...topic, to } };
    } else {
      updatedTopic = { sinkProps, update: { ...currSink } };
    }
  }
  return updatedTopic;
};

export const addColumn = ({ configs, update }) => {
  const { columns = [] } = configs;
  const {
    columnName: name,
    newColumnName: newName,
    currType: dataType,
    parentValues,
  } = update;

  let order = 0;
  if (isEmpty(columns)) {
    order = 1;
  } else {
    order = columns[columns.length - 1].order + 1;
  }

  const newColumn = {
    order,
    name,
    newName,
    dataType,
  };

  const updatedConfigs = {
    ...parentValues,
    columns: [...columns, newColumn],
  };

  return updatedConfigs;
};

export const deleteColumnRow = ({ configs, update }) => {
  const { parentValues, currRow } = update;

  const { columns } = configs;
  const updatedColumns = columns
    .filter(column => column.order !== currRow)
    .map((column, index) => ({ ...column, order: ++index }));

  const updatedConfigs = { ...parentValues, columns: [...updatedColumns] };
  return updatedConfigs;
};

export const moveColumnRowUp = ({ configs, update }) => {
  const { columns } = configs;
  const { order, parentValues } = update;

  if (order === 1) return;

  const index = columns.findIndex(s => s.order === order);

  const updatedColumns = [
    ...columns.slice(0, index - 1),
    columns[index],
    columns[index - 1],
    ...columns.slice(index + 1),
  ].map((columns, index) => ({ ...columns, order: ++index }));
  const updatedConfigs = { ...parentValues, columns: [...updatedColumns] };

  return updatedConfigs;
};

export const moveColumnRowDown = ({ configs, update }) => {
  const { columns } = configs;
  const { order, parentValues } = update;

  if (order === columns.length) return;

  const index = columns.findIndex(s => s.order === order);

  const updatedColumns = [
    ...columns.slice(0, index),
    columns[index + 1],
    columns[index],
    ...columns.slice(index + 2),
  ].map((columns, index) => ({ ...columns, order: ++index }));
  const updatedConfigs = { ...parentValues, columns: [...updatedColumns] };

  return updatedConfigs;
};

export const switchType = type => {
  switch (type) {
    case 'STRING':
      return 'text';
    case 'INT':
    case 'LONG':
    case 'SHORT':
    case 'DOUBLE':
      return 'number';
    case 'PASSWORD':
      return 'password';
    default:
      // Don't render the field since we can't recognize the type
      return null;
  }
};

export const getEditable = ({ key, defaultEditable }) => {
  // we're overriding these two editable values here as
  // - name: is a value that can only be filled once and can't be changed later on
  // - connector_class: cannot be edited in the UI, or it will simply break
  return key === 'name' || key === 'connector_class' ? false : defaultEditable;
};

export const changeToken = ({ values, targetToken, replaceToken }) => {
  return Object.keys(values).reduce((acc, key) => {
    // Two tokens we use to separate words: `.` and `_`
    // we are doing this here because final form treats `foo.bar`
    // as nested object and expands `foo.bar` to `{ foo: { bar } }`
    // and due to the API restriction, we will need to work on this
    // in the frontend 😅
    const pattern = targetToken === '.' ? /\./g : /_/g;
    const renamedObject = {
      [key.replace(pattern, replaceToken)]: values[key],
    };

    return {
      ...acc,
      ...renamedObject,
    };
  }, {});
};

export const changeKeySeparator = key => {
  let result;
  let arr;
  if (key.includes('.')) {
    arr = key.split('.');
    result = arr.join('_');
  } else {
    arr = key.split('_');
    result = arr.join('.');
  }

  return result;
};

export const useFetchConnectors = props => {
  const [state, setState] = useState(null);
  const [configs, setConfigs] = useState(null);
  const [tasks, setTasks] = useState([]);
  const {
    graph,
    pipeline,
    match: { params },
  } = props;
  const { group } = pipeline;
  const { connectorName } = params;

  useEffect(() => {
    const fetchConnector = async () => {
      const res = await connectorApi.fetchConnector(group, connectorName);
      const result = get(res, 'data.result', null);
      if (result) {
        const { settings, tasksStatus } = result;
        const { topicKeys, name } = settings;

        // In order to sync the `running state` of the connector, we're
        // getting state from pipeline `graph` instead of the connector itself
        const currentConnector = graph.find(g => g.name === name);
        const { state } = currentConnector;
        const topicName = get(topicKeys, '[0].name', '');

        const _settings = changeToken({
          values: settings,
          targetToken: '.',
          replaceToken: '_',
        });

        const workerClusterKey = settings.workerClusterKey.name;

        const configs = {
          ..._settings,
          topicKeys: topicName,
          workerClusterKey,
        };
        setTasks(tasksStatus);
        setConfigs(configs);
        setState(state);
      }
    };

    fetchConnector();
  }, [connectorName, graph, group]);
  return [state, setState, configs, setConfigs, tasks];
};

export const useTopics = props => {
  const [topics, setTopics] = useState([]);

  useEffect(() => {
    const loadTopics = () => {
      const { pipelineTopics } = props;
      setTopics(pipelineTopics.map(topic => topic.name));
    };

    loadTopics();
  }, [props]);

  return topics;
};

export const useTestConfig = props => {
  const [isTestingConfig, setIsTestingConfig] = useState(false);
  const { showMessage } = useSnackbar();

  const {
    tags: { workerClusterName },
  } = props.pipeline;

  const handleTestConfig = async (e, values) => {
    const topic = getCurrTopicId({
      originals: props.globalTopics,
      target: values.topicKeys,
    });

    const group = workerClusterName;
    const topicKeys = Array.isArray(topic) ? topic : [{ group, name: topic }];

    const _values = changeToken({
      values,
      targetToken: '_',
      replaceToken: '.',
    });

    const params = { ..._values, topicKeys };
    setIsTestingConfig(true);
    try {
      const res = await validateConnector(params);
      setIsTestingConfig(false);
      const isSuccess = get(res, 'data.isSuccess', false);

      if (isSuccess) {
        showMessage(MESSAGES.TEST_SUCCESS);
      }
    } catch (error) {
      showMessage(error.message);
    }
  };

  return [isTestingConfig, handleTestConfig];
};

export const updateComponent = ({
  updatedConfigs,
  updateHasChanges,
  setConfigs,
}) => {
  updateHasChanges(true);
  setConfigs(updatedConfigs);
};

export const handleColumnChange = ({ configs, ...rest }) => {
  const handleColumnChange = update => {
    const updatedConfigs = addColumn({ configs, update });
    updateComponent({ updatedConfigs, ...rest });
  };

  return handleColumnChange;
};

export const handleColumnRowDelete = ({ configs, ...rest }) => {
  const handleColumnRowDelete = update => {
    const updatedConfigs = deleteColumnRow({ configs, update });
    updateComponent({ updatedConfigs, ...rest });
  };

  return handleColumnRowDelete;
};

export const handleColumnRowUp = ({ configs, ...rest }) => {
  const handleColumnRowUp = (e, update) => {
    e.preventDefault();
    const updatedConfigs = moveColumnRowUp({ configs, update });

    if (updatedConfigs) updateComponent({ updatedConfigs, ...rest });
  };

  return handleColumnRowUp;
};

export const handleColumnRowDown = ({ configs, ...rest }) => {
  const handleColumnRowDown = (e, update) => {
    e.preventDefault();
    const updatedConfigs = moveColumnRowDown({ configs, update });

    if (updatedConfigs) updateComponent({ updatedConfigs, ...rest });
  };

  return handleColumnRowDown;
};

export const handleStartConnector = async params => {
  await triggerConnector(params);
};

export const handleStopConnector = async params => {
  await triggerConnector(params);
};

const triggerConnector = async ({ action, props, setState, showMessage }) => {
  const { group } = props.pipeline;
  const { connectorName } = props.match.params;

  let response;
  if (action === CONNECTOR_ACTIONS.start) {
    try {
      response = await connectorApi.startConnector(group, connectorName);
    } catch (error) {
      showMessage(error.message);
    }
  } else {
    try {
      response = await connectorApi.stopConnector(group, connectorName);
    } catch (error) {
      showMessage(error.message);
    }
  }

  handleTriggerConnectorResponse({
    action,
    response,
    props,
    setState,
    showMessage,
  });
};

const handleTriggerConnectorResponse = ({
  action,
  response,
  props,
  setState,
  showMessage,
}) => {
  const isSuccess = get(response, 'data.isSuccess', false);
  if (!isSuccess) return;

  const { match, graph, updateGraph } = props;
  const connectorName = get(match, 'params.connectorName', null);
  const state = get(response, 'data.result.state');
  setState(state);
  const currSink = findByGraphName(graph, connectorName);
  const update = { ...currSink, state };
  updateGraph({ update, dispatcher: { name: 'CONNECTOR' } });

  if (action === CONNECTOR_ACTIONS.start) {
    if (!isNull(state)) {
      showMessage(MESSAGES.START_CONNECTOR_SUCCESS);
    }
  }
};

export const handleDeleteConnector = async (isRunning, props, showMessage) => {
  const { refreshGraph, history, pipeline } = props;
  const { connectorName, workspaceName } = props.match.params;
  const { name, flows, group } = pipeline;

  if (isRunning) {
    showMessage(
      `The connector is running! Please stop the connector first before deleting`,
    );

    return;
  }

  try {
    const connectorResponse = await connectorApi.deleteConnector(
      group,
      connectorName,
    );
    const connectorHasDeleted = get(connectorResponse, 'data.isSuccess', false);

    const updatedFlows = flows.filter(flow => flow.from.name !== connectorName);
    const pipelineResponse = await pipelineApi.updatePipeline({
      name,
      group,
      params: {
        name,
        flows: updatedFlows,
      },
    });

    const pipelineHasUpdated = get(pipelineResponse, 'data.isSuccess', false);

    if (connectorHasDeleted && pipelineHasUpdated) {
      showMessage(`${MESSAGES.CONNECTOR_DELETION_SUCCESS} ${connectorName}`);
      await refreshGraph();

      const path = `/pipelines/edit/${workspaceName}/${name}`;
      history.push(path);
    }
  } catch (error) {
    showMessage(error.message);
  }
};

export const handleSave = async (props, values, showMessage) => {
  const { globalTopics, graph, updateGraph, pipeline } = props;
  const { connectorName } = props.match.params;
  const {
    group: pipelineGroup,
    tags: { workerClusterName },
  } = pipeline;

  const topic = getCurrTopicId({
    originals: globalTopics,
    target: values.topicKeys,
  });

  const topicGroup = workerClusterName;
  const topicKeys = Array.isArray(topic)
    ? topic
    : [{ group: topicGroup, name: topic }];

  const workerClusterKey = {
    group: 'default',
    name: workerClusterName,
  };

  const _values = changeToken({
    values,
    targetToken: '_',
    replaceToken: '.',
  });

  const params = {
    ..._values,
    topicKeys,
    workerClusterKey,
    name: connectorName,
  };

  try {
    await connectorApi.updateConnector({
      name: connectorName,
      group: pipelineGroup,
      params,
    });
  } catch (error) {
    showMessage(error.message);
  }

  const { sinkProps, update } = getUpdatedTopic({
    currTopicName: topic,
    configs: values,
    originalTopics: globalTopics,
    graph,
    connectorName,
  });

  updateGraph({ update, dispatcher: { name: 'CONNECTOR' }, ...sinkProps });
};

export const getDisplayValue = ({
  configValue,
  defaultValue,
  newKey,
  valueType,
}) => {
  if (newKey === 'from' || newKey === 'to') {
    // A patch for stream app since it supplys an empty array: `[]`
    // as the default value for `from` and `to` fields. However,
    // connectors are using: `null` as the default

    if (Array.isArray(configValue) && isEmpty(configValue)) {
      return null;
    }
  }

  // The configValue could be a `Boolean` value, so we have
  // to handle it differently, otherwise, it will always
  // return back `true` here
  if (typeof configValue === 'boolean') {
    return configValue;
  }

  // Since backend doesn't give us a valid value of the `BOOLEAN` type
  // we need to set a valid default value for it
  if (valueType === 'BOOLEAN') {
    return configValue ? configValue : false;
  }

  // We only want to display key `name` for users
  const keyTypes = ['OBJECT_KEY'];

  if (keyTypes.includes(valueType)) {
    return configValue.name || configValue;
  }

  // handle other types normally here
  return configValue ? configValue : defaultValue;
};

export const sortByOrder = (a, b) => a.orderInGroup - b.orderInGroup;

export const getRenderData = ({ state, defs, configs }) => {
  const isRunning = Boolean(state); // Any state indicates the connector is running

  const data = defs
    .sort(sortByOrder)
    .filter(def => !def.internal) // internal defs are not meant to be seen by users
    .map(def => {
      const { key, defaultValue, valueType } = def;
      const newKey = changeKeySeparator(key);
      const configValue = configs[newKey];

      const displayValue = getDisplayValue({
        configValue,
        defaultValue,
        valueType,
        newKey,
      });

      const editable = getEditable({
        key: newKey,
        defaultEditable: def.editable,
      });

      return {
        ...def,
        displayValue,
        editable,
        isRunning,
        key: newKey,
      };
    });

  return data;
};

export const groupBy = (array, fn) => {
  let groups = {};

  array.forEach(o => {
    let group = JSON.stringify(fn(o));
    groups[group] = groups[group] || [];
    groups[group].push(o);
  });

  return Object.keys(groups).map(group => {
    return groups[group];
  });
};

export const renderer = props => {
  const {
    formData,
    topics,
    handleColumnChange,
    handleColumnRowDelete,
    handleColumnRowUp,
    handleColumnRowDown,
    parentValues,
    freePorts,
  } = props;
  const dataType = ['String'];
  const tableActions = ['Up', 'Down', 'Delete'];

  const renderWithValueType = params => {
    const {
      valueType,
      key,
      displayName,
      documentation,
      editable,
      isRunning,
      tableKeys,
      displayValue,
      required,
    } = params;
    const columnTableHeader = tableKeys.concat(tableActions);

    switch (valueType) {
      case 'STRING':
      case 'CLASS':
      case 'PASSWORD':
      case 'JDBC_TABLE':
      case 'TAGS':
      case 'INT':
      case 'LONG':
      case 'SHORT':
      case 'DOUBLE':
        const inputType = switchType(valueType);

        return (
          <FormControl key={key} fullWidth margin="normal">
            <Field
              type={inputType}
              component={InputField}
              label={displayName}
              id={displayName}
              helperText={documentation}
              width="100%"
              name={key}
              disabled={!editable || isRunning}
              inputProps={{ 'data-testid': key }}
              required={required}
            />
          </FormControl>
        );

      case 'PORT':
        return (
          <FormControl key={key} fullWidth margin="normal">
            <Field
              type="number"
              component={InputField}
              label={displayName}
              id={displayName}
              helperText={documentation}
              width="100%"
              name={key}
              disabled={!editable || isRunning}
              required={required}
              inputProps={{
                'data-testid': key,
                min: 0,
                max: 65535,
              }}
            />
          </FormControl>
        );

      case 'BOOLEAN':
        return (
          <FormControl key={key} fullWidth margin="normal">
            <Field
              label={displayName}
              name={key}
              type="checkbox"
              helperText={documentation}
              component={Checkbox}
              disabled={!editable || isRunning}
              required={required}
              inputProps={{ 'data-testid': key }}
            />
          </FormControl>
        );

      case 'TABLE':
        return (
          <FormControl key={key} fullWidth margin="normal">
            <ColumnTable
              parentValues={parentValues}
              headers={columnTableHeader}
              data={displayValue}
              dataTypes={dataType}
              handleColumnChange={handleColumnChange}
              handleColumnRowDelete={handleColumnRowDelete}
              handleColumnRowUp={handleColumnRowUp}
              handleColumnRowDown={handleColumnRowDown}
              disabled={!editable || isRunning}
            />
          </FormControl>
        );
      case 'BINDING_PORT':
        return (
          <FormControl key={key} fullWidth margin="normal">
            <Field
              label={displayName}
              id={displayName}
              list={freePorts}
              component={Select}
              name={key}
              width="100%"
              required={required}
              disabled={isRunning}
              inputProps={{ 'data-testid': key }}
            />
          </FormControl>
        );
      default:
        return (
          <FormControl key={key} fullWidth margin="normal">
            <Field
              type="text"
              component={InputField}
              label={displayName}
              id={displayName}
              helperText={documentation}
              width="100%"
              required={required}
              name={key}
              disabled={!editable || isRunning}
              inputProps={{ 'data-testid': key }}
            />
          </FormControl>
        );
    }
  };

  const renderWithReference = params => {
    const {
      reference,
      key,
      displayName,
      isRunning,
      documentation,
      required,
    } = params;

    switch (reference) {
      case 'TOPIC':
        return (
          <FormControl key={key} fullWidth margin="normal">
            <Field
              label={displayName}
              id={displayName}
              list={topics}
              component={Select}
              name={key}
              width="100%"
              required={required}
              disabled={isRunning}
              inputProps={{ 'data-testid': key }}
            />
          </FormControl>
        );
      case 'WORKER_CLUSTER':
        return (
          <FormControl key={key} fullWidth margin="normal">
            <Field
              type="string"
              component={InputField}
              label={displayName}
              id={displayName}
              helperText={documentation}
              width="100%"
              name={key}
              disabled={true}
              required={required}
              inputProps={{ 'data-testid': key }}
            />
          </FormControl>
        );
      default:
        return;
    }
  };

  return formData.map(data => {
    const { reference } = data;
    if (reference === 'NONE') {
      return renderWithValueType(data);
    } else {
      return renderWithReference(data);
    }
  });
};

export const renderForm = props => {
  const groupedDefs = groupBy(props.formData, item => {
    return [item.group];
  });

  const hasTab = groupedDefs.length > 1;

  if (hasTab) {
    return <Tabs {...props} groupedDefs={groupedDefs} renderer={renderer} />;
  } else {
    return groupedDefs.sort().map(def => {
      const renderData = { def, ...props };
      return renderer(renderData);
    });
  }
};
