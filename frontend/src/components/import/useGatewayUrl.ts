import { useState, useCallback, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import axios from 'axios';
import { gatewayApi } from '../../api/client';
import { apiErrorI18nMessage } from '../../utils/apiError';

export function isNonRetryable(e: unknown): boolean {
  return axios.isAxiosError(e) && (e.response?.status === 400 || e.response?.status === 404);
}

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
    let lastLbError: unknown = null;
    for (let i = 0; i < 12; i++) {
      try {
        const res = await gatewayApi.getInfo(namespace, gatewayName);
        if (res.data.ready) { hostname = res.data.hostname; break; }
      } catch (e: unknown) {
        lastLbError = e;
        if (isNonRetryable(e)) {
          setError(apiErrorI18nMessage(e, t, t('import.testPanel.gwNotReady')));
          setLoading(false);
          return;
        }
      }
      if (i < 11) await new Promise(r => setTimeout(r, 5000));
    }
    if (!hostname) {
      setError(apiErrorI18nMessage(lastLbError, t, t('import.testPanel.gwNotReady')));
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
      } catch (e: unknown) {
        if (isNonRetryable(e)) {
          setError(apiErrorI18nMessage(e, t, t('import.testPanel.gwNotReady')));
          setLoading(false);
          return;
        }
      }
      if (i < 29) await new Promise(r => setTimeout(r, 10000));
    }
    setUrl(`http://${hostname}`);
    setPhase('done');
    setLoading(false);
  }, [gatewayName, namespace, t]);

  useEffect(() => { fetch(); }, [fetch]);

  return { url, loading, error, phase, refetch: fetch };
}
