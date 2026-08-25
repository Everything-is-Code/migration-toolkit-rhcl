import { describe, expect, it } from 'vitest';
import { formatDate, statusColor } from './historyUtils';

describe('historyUtils', () => {
  describe('formatDate', () => {
    it('formats a valid ISO timestamp', () => {
      const formatted = formatDate('2024-06-15T12:30:00.000Z');
      expect(formatted).toMatch(/2024/);
      expect(formatted).toMatch(/15/);
    });

    it('returns the input when parsing fails', () => {
      expect(formatDate('not-a-date')).toBe('not-a-date');
    });
  });

  describe('statusColor', () => {
    it('maps known statuses to label colors', () => {
      expect(statusColor('COMPLETED')).toBe('green');
      expect(statusColor('failed')).toBe('red');
      expect(statusColor('PARTIAL')).toBe('orange');
      expect(statusColor('RUNNING')).toBe('blue');
    });
  });
});
