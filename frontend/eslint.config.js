import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import tseslint from "typescript-eslint";

const ENTITY_FOLDERS = ["project", "recordingUnit", "find", "phase", "container", "place"];

// Layering: generic code (panels, fields, components, …) never reaches into one entity's folder,
// and an entity folder never reaches into another's. Shared pieces live at entities/ top level.

const genericLayer = {
  files: ["src/{panels,fields,components,api,auth,styles}/**/*.{ts,tsx}", "src/*.{ts,tsx}"],
  ignores: ["src/mount.ts", "**/*.test.{ts,tsx}"],
  rules: {
    "no-restricted-imports": ["error", {
      patterns: [{
        regex: `entities/(${ENTITY_FOLDERS.join("|")})(/|$)`,
        message: "Generic code must not import an entity folder: move the shared piece up or inject it through the entity config.",
      }],
    }],
  },
};

const rulesLayer = {
  files: ["src/rules/**/*.ts"],
  ignores: ["**/*.test.ts"],
  rules: {
    "no-restricted-imports": ["error", {
      patterns: [{ regex: "^\\.\\./", message: "rules/ is framework-free and self-contained." }],
    }],
  },
};

const entityLayers = ENTITY_FOLDERS.map((folder) => ({
  files: [`src/entities/${folder}/**/*.{ts,tsx}`],
  ignores: ["**/*.test.{ts,tsx}"],
  rules: {
    "no-restricted-imports": ["error", {
      patterns: [{
        regex: `^\\.\\./(${ENTITY_FOLDERS.filter((f) => f !== folder).join("|")})(/|$)`,
        message: "An entity folder must not import another entity folder: move the shared piece to entities/.",
      }],
    }],
  },
}));

export default tseslint.config(
  { ignores: ["dist/**", "dev/**", "node_modules/**"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: { globals: { ...globals.browser } },
    plugins: { "react-hooks": reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
    },
  },
  {
    files: ["**/*.test.{ts,tsx}"],
    rules: { "@typescript-eslint/no-explicit-any": "off", "@typescript-eslint/no-non-null-assertion": "off" },
  },
  genericLayer,
  rulesLayer,
  ...entityLayers,
);
