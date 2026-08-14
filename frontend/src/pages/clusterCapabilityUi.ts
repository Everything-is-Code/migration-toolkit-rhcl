/**
 * Pure UI helpers for cluster capability surfaces (Connection / Conversion).
 * Kept free of React so vitest can cover WU3 scenarios without a DOM harness.
 */

/** When to show the cluster versions card on Connection. */
export function shouldShowClusterVersionsCard(connected: boolean): boolean {
  return connected === true;
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
