import { fixHttpRoutePort } from './fixHttpRoutePort';
import { detectExternalBackend, normalizeApiVersions } from '../components/import/importUtils';

export const CREDENTIAL_PLACEHOLDER = 'REPLACE_ME';

export function yamlFilesContainPlaceholder(
  yamlFiles: Record<string, string>,
  placeholder = CREDENTIAL_PLACEHOLDER,
): boolean {
  return Object.values(yamlFiles).some(content => content.includes(placeholder));
}

/** Prepare YAML map for cluster apply (namespace force, apiVersion normalize, external port fix). */
export function buildApplyPayload(
  yamlFiles: Record<string, string>,
  namespace: string,
): Record<string, string> {
  const isExternal = detectExternalBackend(yamlFiles);
  const updated: Record<string, string> = {};
  for (const [name, content] of Object.entries(yamlFiles)) {
    let yaml = normalizeApiVersions(content)
      .replace(/^(\s*namespace:\s*).+$/gm, `$1${namespace}`);
    if (isExternal && name === 'httproute.yaml') {
      yaml = fixHttpRoutePort(yaml);
    }
    updated[name] = yaml;
  }
  return updated;
}
