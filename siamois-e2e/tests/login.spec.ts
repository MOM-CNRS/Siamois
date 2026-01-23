import {expect, test} from "@playwright/test";

const SIAMOIS_STAGING = "https://siamois.dvlogys.dev"

test('can login', async ({ page }) => {
    await page.goto(SIAMOIS_STAGING);

    // 1. Remplir l'input email
    await page.locator("input#email").fill('admin@siamois.fr');

    // 2. Remplir l'input password (ID password)
    await page.locator("input#password").fill('siamois');

    // 3. Cliquer sur le bouton submit
    await page.locator("button[type=submit]").click();

    await page.screenshot({ path: 'screenshot.png', fullPage: true });

    await expect(page.locator("div.sia-panel-title")).toBeVisible();
});

test('can log out', async ({ page }) => {
    await page.goto(SIAMOIS_STAGING);

    // 1. Remplir l'input email
    await page.locator("input#email").fill('admin@siamois.fr');

    // 2. Remplir l'input password (ID password)
    await page.locator("input#password").fill('siamois');

    // 3. Cliquer sur le bouton submit
    await page.locator("button[type=submit]").click();

    await page.locator('button.logout-button').click();
    await page.locator("button.ui-confirmdialog-yes").click()

    await expect(page.getByText("Bienvenue sur Siamois")).toBeVisible()
});