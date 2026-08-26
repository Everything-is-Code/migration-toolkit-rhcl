import { describe, it, expect } from 'vitest';
import type { TFunction } from 'i18next';
import { apiErrorMessage, apiErrorI18nMessage, apiErrorI18nMessageAsync } from './apiError';

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

  it('extracts message from new error envelope object', () => {
    const err = { response: { data: { error: { code: 'VALIDATION_FAILED', message: 'Bad input' } } } };
    expect(apiErrorMessage(err, 'fallback')).toBe('Bad input');
  });
});

describe('apiErrorI18nMessage', () => {
  it('returns i18n string for known error code', () => {
    const mockT = (key: string) => `translated:${key}`;
    const err = { response: { data: { error: { code: 'VALIDATION_FAILED', message: 'Bad input' } } } };
    expect(apiErrorI18nMessage(err, mockT as unknown as TFunction)).toBe('translated:error.validationFailed');
  });

  it('falls back to backend message for unknown error code', () => {
    const mockT = (key: string) => `translated:${key}`;
    const err = { response: { data: { error: { code: 'UNKNOWN_CODE', message: 'Some error' } } } };
    expect(apiErrorI18nMessage(err, mockT as unknown as TFunction)).toBe('Some error');
  });

  it('falls back to legacy extraction for non-envelope errors', () => {
    const mockT = (key: string) => `translated:${key}`;
    const err = { response: { data: { error: 'legacy error string' } } };
    expect(apiErrorI18nMessage(err, mockT as unknown as TFunction)).toBe('legacy error string');
  });

  it('returns i18n string for CONNECTION_TEST_FAILED', () => {
    const mockT = (key: string) => `translated:${key}`;
    const err = { response: { data: { error: { code: 'CONNECTION_TEST_FAILED', message: 'Failed' } } } };
    expect(apiErrorI18nMessage(err, mockT as unknown as TFunction)).toBe('translated:error.connectionTestFailed');
  });

  it('returns i18n string for HISTORY_NOT_FOUND', () => {
    const mockT = (key: string) => `translated:${key}`;
    const err = { response: { data: { error: { code: 'HISTORY_NOT_FOUND', message: 'Not found' } } } };
    expect(apiErrorI18nMessage(err, mockT as unknown as TFunction)).toBe('translated:error.historyNotFound');
  });

  it('returns i18n string for GATEWAY_NOT_FOUND', () => {
    const mockT = (key: string) => `translated:${key}`;
    const err = { response: { data: { error: { code: 'GATEWAY_NOT_FOUND', message: 'Not found' } } } };
    expect(apiErrorI18nMessage(err, mockT as unknown as TFunction)).toBe('translated:error.gatewayNotFound');
  });

  it('handles success:false legacy pattern via fallback', () => {
    const mockT = (key: string) => `translated:${key}`;
    const err = { response: { data: { success: false, message: 'Connection failed' } } };
    expect(apiErrorI18nMessage(err, mockT as unknown as TFunction)).toBe('Connection failed');
  });

  it('returns fallback string when provided', () => {
    const mockT = (key: string) => `translated:${key}`;
    expect(apiErrorI18nMessage(null, mockT as unknown as TFunction, 'my fallback')).toBe('my fallback');
  });

  it('parses envelope code from blob response data', async () => {
    const mockT = (key: string) => `translated:${key}`;
    const blob = new Blob(
      [JSON.stringify({ error: { code: 'HISTORY_NOT_FOUND', message: 'History entry not found' } })],
      { type: 'application/json' },
    );
    const err = { response: { data: blob } };
    expect(await apiErrorI18nMessageAsync(err, mockT as unknown as TFunction))
      .toBe('translated:error.historyNotFound');
  });
});
