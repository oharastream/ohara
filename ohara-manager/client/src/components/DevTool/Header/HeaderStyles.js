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

import styled, { css } from 'styled-components';
import Grid from '@material-ui/core/Grid';

export const StyledHeader = styled(Grid)(
  ({ theme }) => css`
    width: 100%;
    height: 48px;
    z-index: ${theme.zIndex.appBar};
    background-color: ${theme.palette.common.white};
    border: 1px solid ${theme.palette.grey[200]};

    .items {
      display: flex;
      height: 48px;
      flex-direction: row;
      justify-content: flex-end;
      align-items: center;

      .item {
        margin: ${theme.spacing(1)}px;
        cursor: pointer;
      }
    }
  `,
);
