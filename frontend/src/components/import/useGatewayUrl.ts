import { useState, useCallback, useEffect } from 'react';
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

  const fetch = useCallback(async () => {
    if (!gatewayName || !namespace) return;
    setLoading(true); setError(null); setPhase('lb');

    let hostname = '';
    for (let i = 0; i < 12; i++) {
      try {
        const res = await gatewayApi.getInfo(namespace, gatewayName);
        if (res.data.ready) { hostname = res.data.hostname; break; }
      } catch (_e) { /* retry */ }
      if (i < 11) await new Promise(r => setTimeout(r, 5000));
    }
    if (!hostname) {
      setError(t('import.testPanel.gwNotReady'));
      setLoading(false);
      return;
    }

    setPhase('dns');
    for (let i = 0; i < 30; i++) {
      try {
        const res = await gatewayApi.getInfo(namespace, gatewayName);
        if (res.data.dnsReady) {
          setUrl(res.data.httpUrl);
          setPhase('done');
          setLoading(false);
          return;
        }
      } catch (_e) { /* retry */ }
      if (i < 29) await new Promise(r => setTimeout(r, 10000));
    }
    setUrl(`http://${hostname}`);
    setPhase('done');
    setLoading(false);
  }, [gatewayName, namespace, t]);

  useEffect(() => { fetch(); }, [fetch]);

  return { url, loading, error, phase, refetch: fetch };
}
