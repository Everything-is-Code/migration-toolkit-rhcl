export function apiErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object') {
    const err = e as {
      message?: string;
      response?: { data?: { error?: string; message?: string; success?: boolean } };
    };
    return err.response?.data?.error || err.response?.data?.message || err.message || fallback;
  }
  if (typeof e === 'string' && e.trim()) {
    return e;
  }
  return fallback;
}
