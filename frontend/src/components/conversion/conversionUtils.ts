/** Match backend toKebabCase for hostname prefill: {kebab}.{clusterDomain}. */
export function toKebabName(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
