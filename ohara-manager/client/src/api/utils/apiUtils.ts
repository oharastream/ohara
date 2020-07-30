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

import axios, { AxiosResponse, AxiosInstance, AxiosTransformer } from 'axios';
import { isArray, isPlainObject, has, cloneDeep, capitalize } from 'lodash';

import {
  ObjectKey,
  Meta,
  Params,
  BasicResponse,
  ApiError,
} from '../apiInterface/basicInterface';
import { FileRequest } from 'api/apiInterface/fileInterface';

// Configurator resources
export enum RESOURCE {
  ZOOKEEPER = 'zookeepers',
  BROKER = 'brokers',
  WORKER = 'workers',
  STREAM = 'streams',
  SHABONDI = 'shabondis',
  CONNECTOR = 'connectors',
  NODE = 'nodes',
  PIPELINE = 'pipelines',
  TOPIC = 'topics',
  LOG = 'logs',
  VALIDATE = 'validate',
  CONTAINER = 'containers',
  INSPECT = 'inspect',
  FILE = 'files',
  OBJECT = 'objects',
}

export enum COMMAND {
  START = 'start',
  STOP = 'stop',
  PAUSE = 'pause',
  RESUME = 'resume',
  REFRESH = 'refresh',
}

interface AxiosData {
  meta?: {
    url?: string;
    method?: string;
    params?: Meta;
    body?: any;
  };
  result: any;
}

const replaceKeyInObject = (
  obj: object,
  originStr: string,
  changeStr: string,
) => {
  if (!obj) return;
  const newObjs: { [k: string]: any }[] = isArray(obj)
    ? cloneDeep(obj)
    : cloneDeep([obj]);
  newObjs.forEach((newObj, i) => {
    if (!isPlainObject(newObj)) return;
    Object.keys(newObj).forEach((key) => {
      if (isPlainObject(newObjs[i][key])) {
        const obj = replaceKeyInObject(newObjs[i][key], originStr, changeStr);
        newObjs[i][key] = obj;
      } else if (key.indexOf(originStr) !== -1) {
        const newKey = key.split(originStr).join(changeStr);
        newObjs[i][newKey] = newObjs[i][key];
        delete newObjs[i][key];
      }
    });
  });
  return isArray(obj) ? newObjs : newObjs[0];
};

const createAxios = ({ timeout = 20000 }: { timeout?: number } = {}) => {
  const instance = axios.create({
    baseURL: '/api',
    headers: { 'Content-Type': 'application/json' },
    validateStatus: (status) => status >= 200 && status < 300,
    // set an acceptable default timeout (20 seconds) to avoid infinite request
    timeout,
    transformRequest: [
      (data: object) => replaceKeyInObject(data, '__', '.'),
      // axios transform request as "string", we need to add the default JSON transformer from axios
      // https://medium.com/itsoktomakemistakes/axios-transform-request-issue-5410d73ba5f2
      ...(axios.defaults.transformRequest as AxiosTransformer[]),
    ],
    transformResponse: [
      ...(axios.defaults.transformResponse as AxiosTransformer[]),
      (data: object) => replaceKeyInObject(data, '.', '__'),
    ],
  });

  // get the query params object from url string
  const axiosParams = (urlString?: string): Params => {
    var params: Params = {};
    if (urlString) {
      new URL(urlString).searchParams.forEach((value, key) => {
        params[key] = value;
      });
    }
    return params;
  };

  // Add a response interceptor
  instance.interceptors.response.use(
    (response) => {
      const enrichRes: AxiosData = {
        meta: {
          url: response.config.url,
          method: response.config.method,
          params: axiosParams(response.request.responseURL),
          body: response.config.data,
        },
        result: response.data,
      };
      return {
        ...response,
        data: enrichRes,
      };
    },
    (error) => {
      //initial the error response data
      let errorRes: AxiosResponse<AxiosData> = {
        status: -1,
        statusText: '',
        headers: {},
        config: {},
        data: {
          result: error.message,
        },
      };
      // the request may not finished yet
      // we need to check if the request is defined to get the params
      if (error.request) {
        errorRes.data.meta = {
          ...errorRes.data.meta,
          params: axiosParams(error.request?.responseURL),
        };
      }
      if (error.response) {
        const {
          status,
          headers,
          statusText,
          config,
          data: errorMessage,
        } = error.response as AxiosResponse<object>;
        errorRes.status = status;
        errorRes.statusText = statusText;
        errorRes.headers = headers;
        errorRes.config = config;
        // the official format of error data from API should be
        // {apiUrl, code, message, stack}
        errorRes.data.result = has(errorMessage, 'message')
          ? errorMessage
          : error.message;
      }
      return Promise.reject(errorRes);
    },
  );

  return instance;
};

// constructor of APIs
export class API {
  private resource: string;
  private axios: AxiosInstance;

  constructor(resource: string) {
    this.axios = createAxios();
    this.resource = resource;
  }

  private convertResponse(res: AxiosResponse<AxiosData>, title: string) {
    return {
      status: res.status,
      data: res.data.result,
      title,
      meta: {
        url: res.data.meta?.url,
        method: res.data.meta?.method,
        params: res.data.meta?.params,
        body: res.data.meta?.body,
      },
    };
  }

  // API Constructor
  private constructError<T extends BasicResponse>(
    errorObj: any,
    title: string,
  ): T {
    const isApiError = (obj: any): obj is ApiError => {
      return obj.message !== undefined;
    };

    const res = errorObj as AxiosResponse<AxiosData>;
    // initialize the error object
    let error: ApiError = {
      code: 'N/A',
      message: '',
      stack: '',
    };

    if (isApiError(res.data.result)) {
      error = res.data.result;
    } else {
      error.message = res.data.result;
    }

    return {
      status: res.status,
      data: { error },
      title,
    } as T;
  }

  async post<T extends BasicResponse>({
    name,
    body,
    queryParams = {},
    options = {},
  }: {
    name: string;
    body?: object;
    queryParams?: object;
    options?: { [k: string]: any };
  }): Promise<T> {
    try {
      const res = await this.axios.post<AxiosData>(`/${this.resource}`, body, {
        params: queryParams,
      });
      const title = options.title
        ? options.title
        : `Create ${this.resource} "${name}" successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `Create ${this.resource} "${name}" failed.`;
      throw this.constructError<T>(error, title);
    }
  }

  async query<T extends BasicResponse>({
    name,
    queryParams = {},
    options = {},
  }: {
    name?: string;
    queryParams?: object;
    options?: { [k: string]: any };
  }): Promise<T> {
    try {
      const res = await this.axios.post<AxiosData>(
        name ? `/${this.resource}/${name}` : `/${this.resource}`,
        undefined,
        {
          params: queryParams,
        },
      );
      const title = options.title
        ? options.title
        : `Inspect ${this.resource} info successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `Inspect ${this.resource} info failed.`;
      throw this.constructError<T>(error, title);
    }
  }

  async put<T extends BasicResponse>({
    name,
    group,
    body,
    options = {},
  }: {
    name?: string;
    group?: string;
    body?: object;
    options?: { [k: string]: any };
  }) {
    let finalParams: object = {};
    let url: string = this.resource;
    if (name) {
      url = `${this.resource}/${name}`;
    }
    if (group) {
      finalParams = { group };
    }

    try {
      const res = await this.axios.put<AxiosData>(url, body, {
        params: finalParams,
      });
      const title = options.title
        ? options.title
        : `Update ${this.resource} "${name}" successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `Update ${this.resource} "${name}" failed.`;
      throw this.constructError<T>(error, title);
    }
  }

  async delete<T extends BasicResponse>({
    name,
    group,
    options = {},
  }: {
    name?: string;
    group?: string;
    options?: { [k: string]: any };
  }) {
    const finalParams = group ? { group } : {};
    const url = name ? `${this.resource}/${name}` : `${this.resource}`;
    try {
      const res = await this.axios.delete<AxiosData>(url, {
        params: finalParams,
      });
      const title = options.title
        ? options.title
        : `Remove ${this.resource} "${name}" successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `Remove ${this.resource} "${name}" failed.`;
      throw this.constructError<T>(error, title);
    }
  }

  async get<T extends BasicResponse>({
    name,
    queryParams = {},
    options = {},
  }: {
    name?: string;
    queryParams?: object;
    options?: { [k: string]: any };
  } = {}) {
    let url: string = this.resource;
    if (name) {
      url = `${this.resource}/${name}`;
    }
    try {
      const res = await this.axios.get<AxiosData>(url, {
        params: queryParams,
      });
      const title = options.title
        ? options.title
        : `Get ${this.resource} "${name ? name : 'list'}" successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `Get ${this.resource} "${name ? name : 'list'}" failed.`;
      throw this.constructError<T>(error, title);
    }
  }

  async execute<T extends BasicResponse>({
    objectKey,
    action,
    queryParams = {},
    options = {},
  }: {
    objectKey: ObjectKey;
    action: COMMAND;
    queryParams?: object;
    options?: { [k: string]: any };
  }): Promise<T> {
    const finalParams = objectKey
      ? { group: objectKey.group, ...queryParams }
      : queryParams;
    try {
      const res = await this.axios.put<AxiosData>(
        `/${this.resource}/${objectKey.name}/${action}`,
        undefined,
        {
          params: finalParams,
        },
      );
      const title = options.title
        ? options.title
        : `${capitalize(action)} ${this.resource} "${
            objectKey.name
          }" successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `${capitalize(action)} ${this.resource} "${objectKey.name}" failed.`;
      throw this.constructError<T>(error, title);
    }
  }

  async addNode<T extends BasicResponse>({
    objectKey,
    nodeName,
    queryParams = {},
    options = {},
  }: {
    objectKey: ObjectKey;
    nodeName: string;
    queryParams?: object;
    options?: { [k: string]: any };
  }): Promise<T> {
    const finalParams = objectKey
      ? { group: objectKey.group, ...queryParams }
      : queryParams;
    try {
      const res = await this.axios.put<AxiosData>(
        `/${this.resource}/${objectKey.name}/${nodeName}`,
        undefined,
        {
          params: finalParams,
        },
      );
      const title = options.title
        ? options.title
        : `Add node "${nodeName}" into ${this.resource} "${objectKey.name}" successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `Add node "${nodeName}" into ${this.resource} "${objectKey.name}" failed.`;
      throw this.constructError<T>(error, title);
    }
  }

  async removeNode<T extends BasicResponse>({
    objectKey,
    nodeName,
    queryParams = {},
    options = {},
  }: {
    objectKey: ObjectKey;
    nodeName: string;
    queryParams?: object;
    options?: { [k: string]: any };
  }) {
    const finalParams = objectKey
      ? { group: objectKey.group, ...queryParams }
      : queryParams;
    try {
      const res = await this.axios.delete<AxiosData>(
        `/${this.resource}/${objectKey.name}/${nodeName}`,
        {
          params: finalParams,
        },
      );
      const title = options.title
        ? options.title
        : `Remove node "${nodeName}" into ${this.resource} "${objectKey.name}" successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `Remove node "${nodeName}" into ${this.resource} "${objectKey.name}" failed.`;
      throw this.constructError<T>(error, title);
    }
  }

  async upload<T extends BasicResponse>({
    params,
    options = {},
  }: {
    params: FileRequest;
    options?: { [k: string]: any };
  }): Promise<T> {
    let formData = new FormData();
    formData.append('file', params.file);
    formData.append('group', params.group);
    if (params.tags) {
      formData.append('tags', JSON.stringify(params.tags));
    }

    const config = {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    };
    try {
      const res = await this.axios.post<AxiosData>(
        `/${this.resource}`,
        formData,
        config,
      );
      const title = options.title
        ? options.title
        : `Upload ${this.resource} "${params.name}" successfully.`;
      return this.convertResponse(res, title) as T;
    } catch (error) {
      const title = options.title
        ? options.title
        : `Upload ${this.resource} "${params.name}" failed.`;
      throw this.constructError<T>(error, title);
    }
  }
}
