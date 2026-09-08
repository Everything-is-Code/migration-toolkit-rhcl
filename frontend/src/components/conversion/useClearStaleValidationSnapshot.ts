import { useEffect } from 'react';
import { useAppState } from '../AppStateContext';
import {
  clearValidationSnapshotIfStale,
  conversionResultsFingerprint,
} from './conversionWorkflowState';

/** Clear validationSnapshot when conversionResults fingerprint changes (#313). */
export function useClearStaleValidationSnapshot(): void {
  const { appState, setAppState } = useAppState();
  const fingerprint = conversionResultsFingerprint(appState.conversionResults);

  useEffect(() => {
    setAppState(prev => {
      const cleared = clearValidationSnapshotIfStale(prev.validationSnapshot, fingerprint);
      if (cleared === prev.validationSnapshot) {
        return prev;
      }
      return { ...prev, validationSnapshot: cleared };
    });
  }, [fingerprint, setAppState]);
}
