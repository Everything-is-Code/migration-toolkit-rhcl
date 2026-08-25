import type { TFunction } from 'i18next';

export function apiErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object') {
    const err = e as {
      message?: string;
      response?: {
        data?: {
          error?: string | { code?: string; message?: string };
          message?: string;
          success?: boolean;
        };
      };
    };
    const errorField = err.response?.data?.error;
    if (errorField && typeof errorField === 'object' && errorField.message) {
      return errorField.message;
    }
    if (typeof errorField === 'string') return errorField;
    return err.response?.data?.message || err.message || fallback;
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
  if (e && typeof e === 'object') {
    const err = e as {
      response?: {
        data?: {
          error?: string | { code?: string; message?: string };
        };
      };
    };
    const errorField = err.response?.data?.error;
    if (errorField && typeof errorField === 'object' && errorField.code) {
      const i18nKey = ERROR_CODE_I18N[errorField.code];
      if (i18nKey) {
        return t(i18nKey);
      }
      return errorField.message || fallback || 'An error occurred';
    }
  }
  return apiErrorMessage(e, fallback || 'An error occurred');
}
