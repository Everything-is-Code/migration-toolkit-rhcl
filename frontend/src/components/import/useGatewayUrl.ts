import { useState, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { gatewayApi } from '../../api/client';

interface GatewayUrlState {
  url: string | null;
  loading: boolean;
  error: string | null;
  phase: 'lb' | 'dns' | 'done';
  refetch: () => void;
}

export function useGatewayUrl(gatewayName: string | undefined, namespace: string): GatewayUrlState {
  const { t } = useTranslation();
  const [url, setUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [phase, setPhase] = useState<'lb' | 'dns' | 'done'>('lb');
  const controllerRef = useRef<AbortController | null>(null);

  const fetch = useCallback(async () => {
    if (!gatewayName || !namespace) return;
    setLoading(true); setError(null); setPhase('lb');

    let hostname = '';
    for (let i = 0; i < 12; i++) {
      if (controllerRef.current?.signal.aborted) return;
      try {
        const res = await gatewayApi.getInfo(namespace, gatewayName);
        if (res.data.ready) { hostname = res.data.hostname; break; }
      } catch (_e) { /* retry */ }
      if (i < 11) await new Promise(r => setTimeout(r, 5000));
    }
    if (controllerRef.current?.signal.aborted) return;
    if (!hostname) {
      if (controllerRef.current?.signal.aborted) return;
      setError(t('import.testPanel.gwNotReady'));
      setLoading(false);
      return;
    }

    setPhase('dns');
    for (let i = 0; i < 30; i++) {
      if (controllerRef.current?.signal.aborted) return;
      try {
        const res = await gatewayApi.getInfo(namespace, gatewayName);
        if (res.data.dnsReady) {
          if (controllerRef.current?.signal.aborted) return;
          setUrl(res.data.httpUrl);
          setPhase('done');
          setLoading(false);
          return;
        }
      } catch (_e) { /* retry */ }
      if (i < 29) await new Promise(r => setTimeout(r, 10000));
    }
    if (controllerRef.current?.signal.aborted) return;
    setUrl(`http://${hostname}`);
    setPhase('done');
    setLoading(false);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gatewayName, namespace]);

  useEffect(() => {
    const controller = new AbortController();
    controllerRef.current = controller;
    fetch();
    return () => { controller.abort(); };
  }, [fetch]);

  return { url, loading, error, phase, refetch: fetch };
}
