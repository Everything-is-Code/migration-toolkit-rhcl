import { describe, expect, it } from 'vitest';
import { toKebabName } from './conversionUtils';

describe('toKebabName', () => {
  it('lowercases and replaces non-alphanumeric runs with hyphens', () => {
    expect(toKebabName('My API Service')).toBe('my-api-service');
  });

  it('strips leading and trailing hyphens', () => {
    expect(toKebabName('--hello--')).toBe('hello');
  });

  it('handles empty-ish input', () => {
    expect(toKebabName('---')).toBe('');
  });
});
