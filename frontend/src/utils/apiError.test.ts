import { describe, it, expect } from 'vitest';
import { apiErrorMessage } from './apiError';

describe('apiErrorMessage', () => {
  it('returns response.data.error from axios-like error', () => {
    const e = { response: { data: { error: 'Not found' } }, message: 'Request failed' };
    expect(apiErrorMessage(e, 'fallback')).toBe('Not found');
  });

  it('returns response.data.message when error field is absent', () => {
    const e = { response: { data: { message: 'Service unavailable' } }, message: 'Request failed' };
    expect(apiErrorMessage(e, 'fallback')).toBe('Service unavailable');
  });

  it('handles response.data with success+message shape', () => {
    const e = { response: { data: { success: false, message: 'Quota exceeded' } }, message: 'Request failed' };
    expect(apiErrorMessage(e, 'fallback')).toBe('Quota exceeded');
  });

  it('falls back to error.message when no response.data fields', () => {
    const e = new Error('Network error');
    expect(apiErrorMessage(e, 'fallback')).toBe('Network error');
  });

  it('returns string error directly', () => {
    expect(apiErrorMessage('Something broke', 'fallback')).toBe('Something broke');
  });

  it('returns fallback for null', () => {
    expect(apiErrorMessage(null, 'fallback')).toBe('fallback');
  });

  it('returns fallback for undefined', () => {
    expect(apiErrorMessage(undefined, 'fallback')).toBe('fallback');
  });

  it('returns fallback for object with no useful fields', () => {
    expect(apiErrorMessage({ code: 42 }, 'fallback')).toBe('fallback');
  });

  it('returns fallback for empty string', () => {
    expect(apiErrorMessage('', 'fallback')).toBe('fallback');
  });

  it('returns fallback for whitespace-only string', () => {
    expect(apiErrorMessage('   ', 'fallback')).toBe('fallback');
  });
});
