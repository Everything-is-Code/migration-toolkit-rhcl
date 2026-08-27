import { test, expect } from '@playwright/test';
import {
  backToApiList,
  connectTo3scale,
  goToConvertPage,
  openYamlPreview,
  readYamlTab,
  runConversion,
  selectProduct,
} from './helpers';
import { assertProductYaml } from './yaml-assertions';
import { SEED_YAML_EXPECTATIONS } from './yaml-expectations';

const labConfigured =
  Boolean(process.env.THREESCALE_ADMIN_URL?.trim()) &&
  Boolean(process.env.THREESCALE_ACCESS_TOKEN?.trim());

test.describe('Migration wizard — YAML verification (live 3scale)', () => {
  test.skip(!labConfigured, 'Set THREESCALE_ADMIN_URL and THREESCALE_ACCESS_TOKEN');

  for (const [productKey, expectations] of Object.entries(SEED_YAML_EXPECTATIONS)) {
    test(`${productKey} — generated YAML tabs match expectations`, async ({ page }) => {
      await connectTo3scale(page);
      await selectProduct(page, expectations.productLabel);
      await goToConvertPage(page);
      await runConversion(page);
      await openYamlPreview(page);
      await assertProductYaml(page, expectations, productKey);
    });
  }

  test('switching API updates httproute metadata (#229)', async ({ page }) => {
    await connectTo3scale(page);

    await selectProduct(page, SEED_YAML_EXPECTATIONS.rhcl_seed_claim_role_chain.productLabel);
    await goToConvertPage(page);
    await runConversion(page);
    await openYamlPreview(page);
    const firstRoute = await readYamlTab(page, 'httproute.yaml');
    expect(firstRoute).toContain('rhcl-seed-claim-role-chain-route');

    await backToApiList(page);
    await selectProduct(page, SEED_YAML_EXPECTATIONS.rhcl_seed_claim_cache_chain.productLabel);
    await goToConvertPage(page);
    await runConversion(page);
    await openYamlPreview(page);

    const secondRoute = await readYamlTab(page, 'httproute.yaml');
    expect(secondRoute).toContain('rhcl-seed-claim-cache-chain-route');
    expect(secondRoute).not.toContain('rhcl-seed-claim-role-chain-route');
  });
});
