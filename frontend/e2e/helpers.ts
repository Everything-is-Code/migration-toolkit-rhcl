import { expect, type Page } from '@playwright/test';

export function requireEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`Missing required env var: ${name}`);
  }
  return value;
}

export async function connectTo3scale(page: Page): Promise<void> {
  const adminUrl = requireEnv('THREESCALE_ADMIN_URL');
  const token = requireEnv('THREESCALE_ACCESS_TOKEN');

  await page.goto('/');
  await page.getByRole('textbox', { name: '3scale URL' }).fill(adminUrl);
  await page.getByRole('button', { name: 'Show token' }).click();
  await page.getByRole('textbox', { name: 'Access Token' }).fill(token);
  await page.getByRole('button', { name: 'Test Connection' }).click();
  await expect(page.getByRole('button', { name: 'Next: API List' })).toBeEnabled({
    timeout: 30_000,
  });
  await page.getByRole('button', { name: 'Next: API List' }).click();
  await waitForApiList(page);
}

export async function waitForApiList(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: 'API List' })).toBeVisible();
  await expect(page.getByText('Fetching APIs from 3scale', { exact: false })).toHaveCount(0, {
    timeout: 120_000,
  });
  await expect(page.getByRole('list', { name: 'API list' })).toBeVisible({ timeout: 120_000 });
}

function productRow(page: Page, productLabel: string | RegExp) {
  return page
    .getByRole('list', { name: 'API list' })
    .getByRole('listitem')
    .filter({ hasText: productLabel });
}

export async function selectProduct(page: Page, productLabel: string | RegExp): Promise<void> {
  await waitForApiList(page);

  const goFirst = page.getByRole('button', { name: 'Go to first page' });
  if (await goFirst.isVisible().catch(() => false)) {
    if (await goFirst.isEnabled().catch(() => false)) {
      await goFirst.click();
      await waitForApiList(page);
    }
  }

  const row = productRow(page, productLabel);
  let attempts = 0;
  while (!(await row.first().isVisible().catch(() => false)) && attempts < 10) {
    const next = page.getByRole('button', { name: 'Go to next page' }).first();
    if (!(await next.isEnabled().catch(() => false))) {
      break;
    }
    await next.click();
    await waitForApiList(page);
    attempts += 1;
  }

  await expect(row.first()).toBeVisible({ timeout: 15_000 });
  await row.first().click();
  await page.getByRole('button', { name: 'Next: Compatibility Check' }).click();
}

export async function goToConvertPage(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Generate YAML' }).click();
}

export async function runConversion(page: Page): Promise<void> {
  const convert = page.getByRole('button', { name: /^Generate YAML$|^Re-generate$/ });
  await convert.click();
  await expect(page.getByRole('button', { name: 'Next: YAML Preview' })).toBeEnabled({
    timeout: 120_000,
  });
}

export async function openYamlPreview(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Next: YAML Preview' }).click();
}

export async function readYamlTab(page: Page, filename: string): Promise<string> {
  await page.getByRole('tab', { name: filename, exact: true }).click();
  const panel = page.getByRole('tabpanel', { name: filename, exact: true });
  await expect(panel).toBeVisible();
  return (await panel.innerText()) ?? '';
}

export async function backToApiList(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Back', exact: true }).click();
  await page.getByRole('button', { name: 'Back' }).click();
  await page.getByRole('button', { name: 'Cancel' }).click();
  await waitForApiList(page);
}
