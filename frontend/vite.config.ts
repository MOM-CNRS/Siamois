import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/**
 * This app is not a standalone SPA: it's built as a static UMD-ish bundle mounted by the JSF
 * overview panel host page (see src/mount.ts). Output goes to a fixed, unhashed filename so the
 * JSF template can reference it with a plain <script>/<link> tag; cache-busting can be revisited
 * once this ships behind a feature flag in a later phase.
 */
export default defineConfig({
  plugins: [react()],
  base: "/resources/react-ru-panel/",
  build: {
    outDir: "dist",
    emptyOutDir: true,
    rollupOptions: {
      input: "src/mount.ts",
      output: {
        entryFileNames: "recording-unit-panel.js",
        chunkFileNames: "recording-unit-panel-[name].js",
        assetFileNames: "recording-unit-panel[extname]",
      },
    },
  },
  server: {
    port: 5173,
  },
});
