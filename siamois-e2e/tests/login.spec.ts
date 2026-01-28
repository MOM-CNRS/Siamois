import { test, expect } from '@playwright/test';

const SIAMOIS_STAGING = 'https://siamois.dvlogys.dev';
const EMAIL = process.env.TEST_EMAIL || '';
const PASSWORD = process.env.TEST_PASSWORD || '';

test.describe('Fonctionnalités nécessitant une connexion', () => {

    test.describe.configure({ mode: 'serial' });

    test.beforeEach(async ({ page }) => {
        if (!EMAIL || !PASSWORD) throw new Error("Variables d'env manquantes");

        await page.goto(SIAMOIS_STAGING);
        await page.locator("input#email").fill(EMAIL);
        await page.locator("input#password").fill(PASSWORD);
        await page.locator("button[type=submit]").click();

        await expect(page.locator("div.sia-panel-title")).toBeVisible();
    });

    test('La page d\'accueil est affichée correctement après connexion', async ({ page }) => {
        await expect(page).toHaveTitle(/Siamois/);
    });

    test('Doit pouvoir se déconnecter', async ({ page }) => {

        await page.locator('button.logout-button').click();
        await page.locator("button.ui-confirmdialog-yes").click();

        await expect(page.getByText("Bienvenue sur Siamois")).toBeVisible();
    });

});