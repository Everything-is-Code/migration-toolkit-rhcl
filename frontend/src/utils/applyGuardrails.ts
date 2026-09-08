import type { ConversionResultItem } from '../api/types';
import type { AppState } from '../components/AppStateContext';
import { conversionResultsFingerprint } from '../components/conversion/conversionWorkflowState';
import { yamlFilesContainPlaceholder } from './clusterApply';

export type ApplyGuardrailReasonKey =
  | 'download.applyDisabledCluster'
  | 'download.applyDisabledValidationMissing'
  | 'download.applyDisabledValidationError'
  | 'download.applyDisabledPlaceholder'
  | 'download.applyDisabledNoYaml';

export interface ApplyGuardrails {
  enabled: boolean;
  reasonKey: ApplyGuardrailReasonKey | null;
}

export function getApplyGuardrails(
  result: ConversionResultItem,
  appState: AppState,
): ApplyGuardrails {
  const yamlFiles = result.yamlFiles ?? {};
  if (Object.keys(yamlFiles).length === 0) {
    return { enabled: false, reasonKey: 'download.applyDisabledNoYaml' };
  }

  if (appState.clusterVersions?.capabilities?.clusterReachable !== true) {
    return { enabled: false, reasonKey: 'download.applyDisabledCluster' };
  }

  const fingerprint = conversionResultsFingerprint(appState.conversionResults);
  const snapshot = appState.validationSnapshot;
  if (!snapshot || snapshot.fingerprint !== fingerprint) {
    return { enabled: false, reasonKey: 'download.applyDisabledValidationMissing' };
  }

  const validation = snapshot.results[result.serviceId];
  if (!validation || validation.items.some(item => item.status === 'ERROR')) {
    return { enabled: false, reasonKey: 'download.applyDisabledValidationError' };
  }

  if (yamlFilesContainPlaceholder(yamlFiles)) {
    return { enabled: false, reasonKey: 'download.applyDisabledPlaceholder' };
  }

  return { enabled: true, reasonKey: null };
}
