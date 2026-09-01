import { useEffect } from 'react';
import { useAppState } from '../AppStateContext';
import { resultsMatchSelection } from './conversionWorkflowState';

/** Drop conversionResults when selectedServices no longer matches (#229). */
export function useClearStaleConversionResults(): void {
  const { appState, setAppState } = useAppState();

  useEffect(() => {
    if (resultsMatchSelection(appState.conversionResults, appState.selectedServices)) {
      return;
    }
    setAppState(prev =>
      resultsMatchSelection(prev.conversionResults, prev.selectedServices)
        ? prev
        : { ...prev, conversionResults: [] },
    );
  }, [appState.conversionResults, appState.selectedServices, setAppState]);
}
