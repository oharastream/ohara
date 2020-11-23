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

import {
  forwardRef,
  useImperativeHandle,
  useRef,
  Fragment,
  createRef,
  memo,
} from 'react';
import { capitalize, flatten, isEqual, omitBy, isFunction } from 'lodash';
import PropTypes from 'prop-types';
import { Form } from 'react-final-form';
import Typography from '@material-ui/core/Typography';

import RenderDefinition from 'components/common/Definitions/RenderDefinition';
import { EDITABLE } from 'components/common/Definitions/Permission';
import * as hooks from 'hooks';

const scrollIntoViewOption = { behavior: 'smooth', block: 'start' };

const PipelinePropertyForm = forwardRef((props, ref) => {
  const files = hooks.useFiles();
  const nodes = hooks.useNodesInWorkspace();

  const {
    definitions = [],
    freePorts,
    initialValues = {},
    onSubmit,
    topics = [],
  } = props;
  const formRef = useRef(null);
  const fieldRefs = {};

  // Apis
  const apis = {
    change: (key, value) => formRef.current.change(key, value),
    getDefinitions: () => flatten(definitions),
    scrollIntoView: (key) => {
      if (fieldRefs[key].current)
        fieldRefs[key].current.scrollIntoView(scrollIntoViewOption);
    },
    submit: () => formRef.current.submit(),
    values: () => formRef.current.getState().values,
  };

  useImperativeHandle(ref, () => apis);

  return (
    <Form
      initialValues={initialValues}
      onSubmit={onSubmit}
      render={({ handleSubmit, form }) => {
        formRef.current = form;
        return (
          <form onSubmit={handleSubmit}>
            {definitions.map((defs) => {
              const title = defs[0].group;
              return (
                <Fragment key={title}>
                  <Typography variant="h4">{capitalize(title)}</Typography>
                  {defs
                    .filter((def) => !def.internal)
                    .map((def) => {
                      fieldRefs[def.key] = createRef();
                      return RenderDefinition({
                        def,
                        topics,
                        files,
                        nodes,
                        ref: fieldRefs[def.key],
                        defType: EDITABLE,
                        freePorts,
                      });
                    })}
                </Fragment>
              );
            })}
          </form>
        );
      }}
    />
  );
});

PipelinePropertyForm.propTypes = {
  definitions: PropTypes.array,
  freePorts: PropTypes.array,
  initialValues: PropTypes.object,
  onSubmit: PropTypes.func,
  topics: PropTypes.array,
};

const areEqual = (prevProps, nextProps) => {
  // The onSubmit handler passing down form the parent component will always be different
  // from previous render, hance omitting from our comparison function
  return isEqual(omitBy(prevProps, isFunction), omitBy(nextProps, isFunction));
};

export default memo(PipelinePropertyForm, areEqual);
