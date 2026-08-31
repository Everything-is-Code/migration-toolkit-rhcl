import type { ApiService, ConversionResultItem } from '../../api/types';

/** True when confirming a different service than the current selection (id change). */
export function shouldClearConversionResults(prevIds: string[], nextId: string): boolean {
  const prevId = prevIds[0];
  return prevId !== undefined && prevId !== nextId;
}

/** Apply API list Next: replace selection; clear conversionResults only on id change. */
export function nextStateAfterServiceSelect(
  prev: {
    selectedServices: ApiService[];
    conversionResults: ConversionResultItem[];
  },
  next: ApiService,
): {
  selectedServices: ApiService[];
  conversionResults: ConversionResultItem[];
} {
  const prevIds = prev.selectedServices.map(s => s.id);
  const clear = shouldClearConversionResults(prevIds, next.id);
  return {
    selectedServices: [next],
    conversionResults: clear ? [] : prev.conversionResults,
  };
}

/**
 * Stable fingerprint for conversion results — ignores array identity.
 * Format: serviceId:historyId|packageName joined by `|`.
 */
export function conversionResultsFingerprint(results: ConversionResultItem[]): string {
  return results
    .map(r => `${r.serviceId}:${r.historyId ?? r.packageName ?? ''}`)
    .join('|');
}

/** Seed YAML viewer edits map from conversion result yamlFiles. */
export function buildEditsFromResults(
  results: ConversionResultItem[],
): Record<number, Record<string, string>> {
  const init: Record<number, Record<string, string>> = {};
  results.forEach((r, i) => {
    if (r.yamlFiles) {
      init[i] = { ...r.yamlFiles };
    }
  });
  return init;
}

/**
 * True when there are no stale results for the current selection.
 * Empty results always match. Otherwise selectedServices[0].id must appear in results.
 */
export function resultsMatchSelection(
  results: ConversionResultItem[],
  selected: ApiService[],
): boolean {
  if (results.length === 0) {
    return true;
  }
  const id = selected[0]?.id;
  if (!id) {
    return false;
  }
  return results.some(r => r.serviceId === id);
}
