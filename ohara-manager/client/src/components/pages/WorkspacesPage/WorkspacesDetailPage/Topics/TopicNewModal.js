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

import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { Form, Field } from 'react-final-form';
import { get } from 'lodash';
import DialogContent from '@material-ui/core/DialogContent';

import * as MESSAGES from 'constants/messages';
import { Dialog } from 'components/common/Mui/Dialog';
import { InputField } from 'components/common/Mui/Form';
import useSnackbar from 'components/context/Snackbar/useSnackbar';
import * as useApi from 'components/controller';
import * as URL from 'components/controller/url';
import { nameValidate, numberValidate } from 'utils/validate';

const TopicNewModal = props => {
  const [isSaving, setIsSaving] = useState(false);
  const { getData: topicRes, postApi: createTopic } = useApi.usePostApi(
    URL.TOPIC_URL,
  );
  const { putApi: startTopic } = useApi.usePutApi(URL.TOPIC_URL);
  const { showMessage } = useSnackbar();
  const { worker } = props;

  const handleClose = () => {
    props.onClose();
  };

  const onSubmit = async (values, form) => {
    setIsSaving(true);

    await createTopic({
      name: values.name,
      numberOfPartitions: Number(values.numberOfPartitions),
      numberOfReplications: Number(values.numberOfReplications),
      brokerClusterKey: {
        group: 'default',
        name: worker.settings.brokerClusterKey.name,
      },
      group: `${worker.settings.name}`,
    });

    const isSuccess = get(topicRes(), 'data.isSuccess', false);
    if (!isSuccess) {
      setTimeout(form.reset);
      showMessage('Topic failed to establish!');
      props.onConfirm();
      handleClose();
      setIsSaving(false);
      return;
    }

    await startTopic(`/${values.name}/start?group=${worker.settings.name}`);
    setIsSaving(false);

    if (isSuccess) {
      setTimeout(form.reset);
      showMessage(MESSAGES.TOPIC_CREATION_SUCCESS);
      props.onConfirm();
      handleClose();
    }
  };

  const Validate = values => {
    const error = {
      ...nameValidate({ values, keys: ['name'] }),
      ...numberValidate({
        values,
        keys: ['numberOfPartitions', 'numberOfReplications'],
      }),
    };
    return error;
  };

  return (
    <Form
      onSubmit={onSubmit}
      initialValues={{}}
      validate={values => Validate(values)}
      render={({ handleSubmit, form, submitting, pristine, invalid }) => {
        return (
          <Dialog
            title="New topic"
            open={props.isActive}
            handleClose={() => {
              form.reset();
              handleClose();
            }}
            loading={isSaving}
            handleConfirm={handleSubmit}
            confirmBtnText="ADD"
            confirmDisabled={submitting || pristine || invalid}
            testId="topic-new-modal"
          >
            <form onSubmit={handleSubmit}>
              <DialogContent>
                <Field
                  label="Topic name"
                  id="topicInput"
                  name="name"
                  component={InputField}
                  placeholder="Kafka Topic"
                  data-testid="name-input"
                  autoFocus
                />
              </DialogContent>
              <DialogContent>
                <Field
                  label="Partitions"
                  id="partInput"
                  name="numberOfPartitions"
                  component={InputField}
                  type="number"
                  placeholder="1"
                  inputProps={{
                    'data-testid': 'partitions-input',
                    min: '1',
                    step: '1',
                  }}
                />
              </DialogContent>
              <DialogContent>
                <Field
                  label="Replication factor"
                  id="repInput"
                  name="numberOfReplications"
                  component={InputField}
                  type="number"
                  placeholder="1"
                  inputProps={{
                    'data-testid': 'replications-input',
                    min: '1',
                    step: '1',
                  }}
                />
              </DialogContent>
            </form>
          </Dialog>
        );
      }}
    />
  );
};

TopicNewModal.propTypes = {
  isActive: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onConfirm: PropTypes.func.isRequired,
  worker: PropTypes.shape({
    settings: PropTypes.shape({
      name: PropTypes.string.isRequired,
      brokerClusterKey: PropTypes.shape({
        group: PropTypes.string.isRequired,
        name: PropTypes.string.isRequired,
      }).isRequired,
    }).isRequired,
  }).isRequired,
};

export default TopicNewModal;
