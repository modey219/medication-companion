/**
 * Wrangler bundles `.txt` files as text modules natively (Workers default
 * module rules). This declaration makes that import typed for TypeScript;
 * Vitest reproduces the same behaviour through a small plugin in
 * vitest.config.ts.
 */
declare module "*.txt" {
  const content: string;
  export default content;
}
