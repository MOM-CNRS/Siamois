/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Builds the main-panel React app as a small set of static assets (JS/CSS/fonts) with fixed,
// predictable filenames, so the JSF templates can reference them without a manifest lookup.
// Maven copies frontend/dist/** into META-INF/resources/resources/react-main-panel/ (see pom.xml),
// a resource path distinct from the (unmerged) RU branch's react-ru-panel/, so both can coexist
// once that branch is reconciled.
export default defineConfig({
  plugins: [react()],
  base: "/resources/react-main-panel/",
  build: {
    outDir: "dist",
    emptyOutDir: true,
    rollupOptions: {
      input: "src/mount.ts",
      output: {
        entryFileNames: "main-panel.js",
        chunkFileNames: "main-panel-[hash].js",
        assetFileNames: "main-panel[extname]",
      },
    },
  },
  server: {
    port: 5174,
  },
  test: {
    environment: "jsdom",
    globals: true,
  },
});
