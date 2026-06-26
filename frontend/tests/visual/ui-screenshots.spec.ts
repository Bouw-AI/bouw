import { test, expect, type Page } from "@playwright/test";

/**
 * Iterates every major screen, opens it deterministically through the mock screen router, waits for
 * a stable render, and captures a full-page screenshot. Output files are named
 * `<project>-<screen>.png` (e.g. `desktop-login.png`, `mobile-chat.png`) under `screenshots/`, which
 * the GitHub Actions workflow uploads as an artifact.
 *
 * Deliberately NO visual assertions: the only failure modes are a crashed page, a render that never
 * settles, or a screenshot that can't be taken.
 */

type ScreenSpec = {
  /** Value passed to `?mockScreen=` — must match a real Screen in the app's screen model. */
  id: string;
  /** A selector unique to the screen that proves it rendered (not the boot "Loading…" state). */
  ready: string;
};

const SCREENS: ScreenSpec[] = [
  { id: "login", ready: ".login-screen" },
  { id: "purechat", ready: ".input-bar" },
  { id: "chat", ready: ".input-bar" },
  { id: "history", ready: ".screen-title" },
  { id: "integrations", ready: ".screen-content" },
  { id: "settings", ready: ".model-settings-screen" },
  { id: "preferences", ready: ".settings-body" },
  { id: "github-repo", ready: ".screen-content" },
  { id: "agent-threads", ready: ".model-settings-screen" },
  { id: "user-details", ready: ".settings-body" }
];

/** Freeze anything that could make a screenshot non-deterministic, without touching production code. */
async function stabilize(page: Page) {
  await page.addStyleTag({
    content: `
      *, *::before, *::after {
        transition: none !important;
        animation: none !important;
        caret-color: transparent !important;
        scroll-behavior: auto !important;
      }
    `
  });
  // Let fonts finish loading so text metrics are stable before we snap.
  await page.evaluate(() => document.fonts?.ready);
}

for (const screen of SCREENS) {
  test(`screenshot: ${screen.id}`, async ({ page }, testInfo) => {
    const errors: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text());
    });
    page.on("pageerror", (error) => errors.push(error.message));

    await page.goto(`/?mockScreen=${screen.id}`);

    // The shell, then the screen's characteristic element, must be visible.
    await expect(page.locator(".device-shell, .login-screen").first()).toBeVisible();
    await expect(page.locator(screen.ready).first()).toBeVisible({ timeout: 15_000 });

    // Mock responses resolve as microtasks (no network), so there's nothing for `networkidle` to wait
    // on. A short settle lets data-loading effects (and the GitHub repo deep-link load) flush.
    await page.waitForTimeout(500);
    await stabilize(page);

    const fileName = `${testInfo.project.name}-${screen.id}.png`;
    await page.screenshot({
      path: `screenshots/${fileName}`,
      fullPage: true,
      animations: "disabled",
      caret: "hide"
    });

    // A blank screen would otherwise pass silently — surface unexpected runtime errors.
    expect(errors, `console/page errors on "${screen.id}": ${errors.join(" | ")}`).toHaveLength(0);
  });
}
