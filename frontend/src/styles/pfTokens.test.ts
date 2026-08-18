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

  it('exports border, panel, surface, and dark-editor tokens as pf-v5 vars', () => {
    expect(tokens.PF_BORDER_COLOR).toBe('var(--pf-v5-global--BorderColor--100)');
    expect(tokens.PF_BORDER_COLOR_LIGHT).toBe('var(--pf-v5-global--BorderColor--300)');
    expect(tokens.PF_COLOR_DEFAULT).toBe('var(--pf-v5-global--Color--100)');
    expect(tokens.PF_PRIMARY_DARK).toBe('var(--pf-v5-global--primary-color--200)');
    expect(tokens.PF_INFO_BG).toBe('var(--pf-v5-global--palette--blue-50)');
    expect(tokens.PF_INFO_BORDER).toBe('var(--pf-v5-global--palette--blue-200)');
    expect(tokens.PF_WARNING_TEXT).toBe('var(--pf-v5-global--warning-color--200)');
    expect(tokens.PF_WARNING_BORDER).toBe('var(--pf-v5-global--warning-color--100)');
    expect(tokens.PF_SURFACE).toBe('var(--pf-v5-global--BackgroundColor--100)');
    expect(tokens.PF_EDITOR_BG).toBe('var(--pf-v5-global--palette--black-900)');
    expect(tokens.PF_EDITOR_FG).toBe('var(--pf-v5-global--Color--light-200)');
    expect(tokens.PF_EDITOR_BORDER).toBe('var(--pf-v5-global--BorderColor--dark-100)');
    expect(tokens.PF_COLOR_ON_DARK).toBe('var(--pf-v5-global--Color--light-100)');
    expect(tokens.PF_BG_DARK).toBe('var(--pf-v5-global--BackgroundColor--dark-100)');
  });

  it('keeps every export as a pf-v5 CSS custom property reference', () => {
    for (const [name, value] of Object.entries(tokens)) {
      expect(value, name).toMatch(/^var\(--pf-v5-global--/);
    }
  });
});
