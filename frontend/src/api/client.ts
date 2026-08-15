import axios from 'axios';
import i18n from '../i18n';
import { ClusterVersionsResponse } from './types';
import { clearPersistedConnection } from '../utils/appStateStorage';

const BASE_URL = import.meta.env.VITE_API_URL || '';

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// Send the current i18n language as Accept-Language so that backend messages
// (e.g. apply.success) match the frontend display language.
api.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = i18n.language || 'en';
  return config;
});

// I-3: drop persisted connection when the API rejects auth.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    if (status === 401 || status === 403) {
      clearPersistedConnection();
    }
    return Promise.reject(error);
  },
);

export const connectionApi = {
  test: (data: { url: string; accessToken: string; tenant?: string }) =>
    api.post('/api/connection/test', data),
};

export const defaultsApi = {
  get: () =>
    api.get<{
      threescale: {
        url: string | null;
        token: string | null;
        configured: boolean;
      };
    }>('/api/defaults'),
};

const bearerHeaders = (accessToken: string) => ({
  Authorization: `Bearer ${accessToken}`,
});

export const servicesApi = {
  list: (url: string, accessToken: string) =>
    api.get('/api/services', { params: { url }, headers: bearerHeaders(accessToken) }),
  get: (id: string, url: string, accessToken: string) =>
    api.get(`/api/services/${id}`, { params: { url }, headers: bearerHeaders(accessToken) }),
  checkCompatibility: (id: string, url: string, accessToken: string, supportedPolicies: string[]) =>
    api.get(`/api/services/${id}/compatibility`, {
      params: { url, supportedPolicies: supportedPolicies.join('|') },
      headers: bearerHeaders(accessToken),
    }),
};

export const conversionApi = {
  convert: (data: {
    threescaleUrl: string;
    accessToken: string;
    tenant?: string;
    namespace: string;
    serviceIds: string[];
    externalBackendUrl?: string;
    supportedPolicies?: string[];
    loggingTarget?: 'gateway' | 'workload';
    anonymousTarget?: 'httproute' | 'gateway';
    includeMigratedFromLabel?: boolean;
    ipCheckMode?: 'authorizationPolicy' | 'authPolicyOpa';
  }) => api.post('/api/convert', data),
};

export const validationApi = {
  validate: (yamlFiles: Record<string, string>) =>
    api.post('/api/validate', yamlFiles),
};

export const downloadApi = {
  downloadZip: (packageName: string, yamlFiles: Record<string, string>) =>
    api.post('/api/download/zip', { packageName, yamlFiles }, { responseType: 'blob' }),
};

export const applyApi = {
  apply: (namespace: string, files: Record<string, string>, source = 'CONVERT', packageName?: string) =>
    api.post('/api/apply', { namespace, files, source, packageName }),
};

export const importApi = {
  uploadZip: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api.post('/api/import/zip', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

export const gatewayApi = {
  getInfo: (namespace: string, name: string) =>
    api.get('/api/gateway/info', { params: { namespace, name } }),
};

export const settingsApi = {
  get: (key: string) => api.get(`/api/settings/${key}`),
  put: (key: string, value: string) => api.put(`/api/settings/${key}`, { value }),
};

export const clusterApi = {
  getDomain: () => api.get<{ domain: string }>('/api/cluster/domain'),
  getVersions: (refresh = false) =>
    api.get<ClusterVersionsResponse>('/api/cluster/versions', {
      params: refresh ? { refresh: true } : undefined,
    }),
};

export const historyApi = {
  list: (page = 0, size = 50) =>
    api.get('/api/history', { params: { page, size } }),
  get: (id: number) => api.get(`/api/history/${id}`),
  downloadZip: (id: number) =>
    api.get(`/api/history/${id}/download`, { responseType: 'blob' }),
  deleteByIds: (ids: number[]) =>
    api.delete('/api/history', { data: ids }),
};

export default api;
