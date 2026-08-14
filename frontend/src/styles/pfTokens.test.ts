import { describe, expect, it } from 'vitest';
import * as tokens from './pfTokens';

describe('pfTokens', () => {
  it('exports shared PF v5 CSS var constants used by pages', () => {
    expect(tokens.PF_COLOR_MUTED).toBe('var(--pf-v5-global--Color--200)');
    expect(tokens.PF_BG_DEFAULT).toBe('var(--pf-v5-global--BackgroundColor--200)');
    expect(tokens.PF_PRIMARY).toBe('var(--pf-v5-global--primary-color--100)');
    expect(tokens.PF_PRIMARY_BG).toBe('var(--pf-v5-global--palette--blue-50)');
    expect(tokens.PF_FONT_SIZE_XS).toBe('var(--pf-v5-global--FontSize--xs)');
    expect(tokens.PF_FONT_SIZE_SM).toBe('var(--pf-v5-global--FontSize--sm)');
    expect(tokens.PF_FONT_SIZE_2XL).toBe('var(--pf-v5-global--FontSize--2xl)');
    expect(tokens.PF_SPACER_SM).toBe('var(--pf-v5-global--spacer--sm)');
    expect(tokens.PF_SUCCESS).toMatch(/^var\(--pf-v5-global--/);
  });

  it('keeps every export as a pf-v5 CSS custom property reference', () => {
    for (const [name, value] of Object.entries(tokens)) {
      expect(value, name).toMatch(/^var\(--pf-v5-global--/);
    }
  });
});
