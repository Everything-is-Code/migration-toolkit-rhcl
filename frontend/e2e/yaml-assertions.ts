import { expect, type Page } from '@playwright/test';
import { readYamlTab } from './helpers';
import type { ProductYamlExpectations } from './yaml-expectations';

export async function assertProductYaml(
  page: Page,
  expectations: ProductYamlExpectations,
  productKey: string,
): Promise<void> {
  for (const [filename, rules] of Object.entries(expectations.files)) {
    const content = await readYamlTab(page, filename);
    expect(content.length, `${productKey}/${filename} should not be empty`).toBeGreaterThan(0);

    for (const fragment of rules.mustContain) {
      expect(content, `${productKey}/${filename} must contain: ${fragment}`).toContain(fragment);
    }
    for (const forbidden of rules.mustNotContain ?? []) {
      expect(content, `${productKey}/${filename} must not contain: ${forbidden}`).not.toContain(
        forbidden,
      );
    }
  }
}
