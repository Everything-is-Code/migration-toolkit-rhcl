import { describe, expect, it } from 'vitest';
import axios from 'axios';
import { isNonRetryable } from './useGatewayUrl';

function axiosError(status: number, code: string) {
  return new axios.AxiosError('fail', 'ERR', undefined, undefined, {
    status,
    data: { error: { code, message: 'backend msg' } },
    statusText: 'Error',
    headers: {},
    config: {} as never,
  });
}

describe('isNonRetryable', () => {
  it('returns true for 400 and 404 axios errors', () => {
    expect(isNonRetryable(axiosError(400, 'VALIDATION_FAILED'))).toBe(true);
    expect(isNonRetryable(axiosError(404, 'GATEWAY_NOT_FOUND'))).toBe(true);
  });

  it('returns false for 502 and non-axios errors', () => {
    expect(isNonRetryable(axiosError(502, 'THREESCALE_CLIENT_ERROR'))).toBe(false);
    expect(isNonRetryable(axiosError(500, 'INTERNAL_ERROR'))).toBe(false);
    expect(isNonRetryable(new Error('network'))).toBe(false);
  });
});
