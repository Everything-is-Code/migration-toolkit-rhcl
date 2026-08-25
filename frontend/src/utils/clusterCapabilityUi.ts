/**
 * Pure UI helpers for cluster capability surfaces (Connection / Conversion).
 * Kept free of React so vitest can cover WU3 scenarios without a DOM harness.
 */

import type { ClusterProfile } from '../api/types';

/** When to show the cluster versions card on Connection. */
export function shouldShowClusterVersionsCard(connected: boolean): boolean {
  return connected === true;
}

/**
 * Profile → Connection page i18n key (avoids nested ternaries in FormSelect labels).
 */
export const CLUSTER_PROFILE_I18N_KEYS: Record<ClusterProfile, string> = {
  auto: 'connection.profile.auto',
  'ocp-4.19': 'connection.profile.ocp419',
  'ocp-4.21': 'connection.profile.ocp421',
};

export function clusterProfileI18nKey(profile: ClusterProfile): string {
  return CLUSTER_PROFILE_I18N_KEYS[profile];
}

/**
 * i18n key for the Conversion CORS capability hint, or null when no cors policy.
 */
export function corsConversionHintKey(
  hasCorsPolicy: boolean,
  corsNative: boolean,
): 'conversion.corsNativeHint' | 'conversion.corsFallbackHint' | null {
  if (!hasCorsPolicy) {
    return null;
  }
  return corsNative ? 'conversion.corsNativeHint' : 'conversion.corsFallbackHint';
}
