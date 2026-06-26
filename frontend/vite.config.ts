/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  base: "/",
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080"
    }
  },
  // Vitest runs the unit tests under src/. The Playwright screenshot specs under tests/visual are
  // driven by `npm run screenshots` (playwright.config.ts), not Vitest, so keep them out of here.
  test: {
    include: ["src/**/*.{test,spec}.{ts,tsx}"]
  }
});