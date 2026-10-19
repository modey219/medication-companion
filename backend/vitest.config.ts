import { readFileSync } from "node:fs";
import { defineConfig } from "vitest/config";

/**
 * Wrangler bundles `.txt` files as text modules natively (Workers default
 * module rules), but plain Vite treats them as asset URLs. This plugin makes
 * `import prompt from "./system-prompt.txt"` behave the same way under Vitest
 * as it does under Wrangler.
 */
function textModulePlugin() {
  return {
    name: "medcompanion-text-modules",
    enforce: "pre" as const,
    load(id: string) {
      const path = id.split("?")[0];
      if (path !== undefined && path.endsWith(".txt")) {
        return `export default ${JSON.stringify(readFileSync(path, "utf8"))};`;
      }
      return null;
    },
  };
}

export default defineConfig({
  plugins: [textModulePlugin()],
  test: {
    environment: "node",
    include: ["tests/**/*.test.ts"],
  },
});
