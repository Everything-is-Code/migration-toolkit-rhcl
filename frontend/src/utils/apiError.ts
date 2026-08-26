import type { TFunction } from 'i18next';

type ErrorResponseData = {
  error?: string | { code?: string; message?: string };
  message?: string;
  success?: boolean;
};

function isBlob(data: unknown): data is Blob {
  return typeof Blob !== 'undefined' && data instanceof Blob;
}

/** Parse axios error `response.data` when blob downloads return JSON errors as Blob. */
export async function normalizeAxiosErrorData(data: unknown): Promise<unknown> {
  if (isBlob(data)) {
    try {
      const text = await data.text();
      return JSON.parse(text) as unknown;
    } catch {
      return data;
    }
  }
  return data;
}

function messageFromResponseData(
  data: ErrorResponseData | undefined,
  fallback: string,
  topLevelMessage?: string,
): string {
  const errorField = data?.error;
  if (errorField && typeof errorField === 'object' && errorField.message) {
    return errorField.message;
  }
  if (typeof errorField === 'string') return errorField;
  return data?.message || topLevelMessage || fallback;
}

function i18nMessageFromResponseData(
  data: ErrorResponseData | undefined,
  t: TFunction,
  fallback: string,
): string {
  const errorField = data?.error;
  if (errorField && typeof errorField === 'object' && errorField.code) {
    const i18nKey = ERROR_CODE_I18N[errorField.code];
    if (i18nKey) {
      return t(i18nKey);
    }
    return errorField.message || fallback;
  }
  return messageFromResponseData(data, fallback);
}

export function apiErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object') {
    const err = e as { message?: string; response?: { data?: ErrorResponseData } };
    if (err.response?.data !== undefined) {
      return messageFromResponseData(err.response.data, fallback, err.message);
    }
    return err.message || fallback;
  }
  if (typeof e === 'string' && e.trim()) {
    return e;
  }
  return fallback;
}

export const ERROR_CODE_I18N: Record<string, string> = {
  VALIDATION_FAILED: 'error.validationFailed',
  THREESCALE_CLIENT_ERROR: 'error.threescaleClient',
  APPLY_FAILED: 'error.applyFailed',
  IMPORT_PARSE_ERROR: 'error.importParse',
  IMPORT_NO_YAML: 'error.importNoYaml',
  INTERNAL_ERROR: 'error.internal',
  CONNECTION_TEST_FAILED: 'error.connectionTestFailed',
  HISTORY_NOT_FOUND: 'error.historyNotFound',
  HISTORY_DOWNLOAD_FAILED: 'error.historyDownloadFailed',
  CLUSTER_ROUTE_NOT_FOUND: 'error.clusterRouteNotFound',
  CLUSTER_ROUTE_HOST_PENDING: 'error.clusterRouteHostPending',
  CLUSTER_DOMAIN_EXTRACT_FAILED: 'error.clusterDomainExtractFailed',
  GATEWAY_NOT_FOUND: 'error.gatewayNotFound',
  SETTINGS_NOT_FOUND: 'error.settingsNotFound',
};

export function apiErrorI18nMessage(e: unknown, t: TFunction, fallback?: string): string {
  const fb = fallback || 'An error occurred';
  if (e && typeof e === 'object') {
    const err = e as { response?: { data?: ErrorResponseData } };
    if (err.response?.data !== undefined) {
      return i18nMessageFromResponseData(err.response.data, t, fb);
    }
  }
  return apiErrorMessage(e, fb);
}

export async function apiErrorI18nMessageAsync(
  e: unknown,
  t: TFunction,
  fallback?: string,
): Promise<string> {
  const fb = fallback || 'An error occurred';
  if (e && typeof e === 'object') {
    const err = e as { message?: string; response?: { data?: unknown } };
    if (err.response?.data !== undefined) {
      const data = await normalizeAxiosErrorData(err.response.data) as ErrorResponseData;
      return i18nMessageFromResponseData(data, t, fb);
    }
    return err.message || fb;
  }
  if (typeof e === 'string' && e.trim()) {
    return e;
  }
  return fb;
}
