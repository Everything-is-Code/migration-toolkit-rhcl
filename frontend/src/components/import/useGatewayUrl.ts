import { useState, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { gatewayApi } from '../../api/client';

interface GatewayUrlState {
  url: string | null;
  loading: boolean;
  error: string | null;
  phase: 'lb' | 'dns' | 'done';
  refetch: () => void;
}

function abortableSleep(ms: number, signal: AbortSignal): Promise<void> {
  if (signal.aborted) {
    return Promise.reject(signal.reason ?? new DOMException('Aborted', 'AbortError'));
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener(
      'abort',
      () => {
        clearTimeout(timer);
        reject(signal.reason ?? new DOMException('Aborted', 'AbortError'));
      },
      { once: true },
    );
  });
}

function isAbortError(e: unknown): boolean {
  if (e instanceof DOMException && e.name === 'AbortError') return true;
  if (isAxiosError(e) && e.code === 'ERR_CANCELED') return true;
  return false;
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
    const signal = controllerRef.current?.signal;
    if (!signal) return;

    setLoading(true);
    setError(null);
    setPhase('lb');

    let hostname = '';
    for (let i = 0; i < 12; i++) {
      if (signal.aborted) return;
      try {
        const res = await gatewayApi.getInfo(namespace, gatewayName, { signal });
        if (res.data.ready) {
          hostname = res.data.hostname;
          break;
        }
      } catch (e) {
        if (isAbortError(e)) return;
      }
      if (i < 11) {
        try {
          await abortableSleep(5000, signal);
        } catch {
          return;
        }
      }
    }

    if (signal.aborted) return;
    if (!hostname) {
      setError(t('import.testPanel.gwNotReady'));
      setLoading(false);
      return;
    }

    setPhase('dns');
    for (let i = 0; i < 30; i++) {
      if (signal.aborted) return;
      try {
        const res = await gatewayApi.getInfo(namespace, gatewayName, { signal });
        if (res.data.dnsReady) {
          setUrl(res.data.httpUrl);
          setPhase('done');
          setLoading(false);
          return;
        }
      } catch (e) {
        if (isAbortError(e)) return;
      }
      if (i < 29) {
        try {
          await abortableSleep(10000, signal);
        } catch {
          return;
        }
      }
    }

    if (signal.aborted) return;
    setUrl(`http://${hostname}`);
    setPhase('done');
    setLoading(false);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gatewayName, namespace]);

  useEffect(() => {
    const controller = new AbortController();
    controllerRef.current = controller;
    fetch();
    return () => {
      controller.abort();
    };
  }, [fetch]);

  return { url, loading, error, phase, refetch: fetch };
}
