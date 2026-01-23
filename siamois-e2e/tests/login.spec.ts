import { test, expect } from '@playwright/test';

const SIAMOIS_STAGING = 'https://siamois.dvlogys.dev';
const EMAIL = process.env.TEST_EMAIL || '';
const PASSWORD = process.env.TEST_PASSWORD || '';

test.describe('Fonctionnalités nécessitant une connexion', () => {

    // On garde le mode série car on utilise le même compte
    test.describe.configure({ mode: 'serial' });

    // --- CE BLOC S'EXECUTE AVANT CHAQUE TEST ---
    test.beforeEach(async ({ page }) => {
        if (!EMAIL || !PASSWORD) throw new Error("Variables d'env manquantes");

        await page.goto(SIAMOIS_STAGING);
        await page.locator("input#email").fill(EMAIL);
        await page.locator("input#password").fill(PASSWORD);
        await page.locator("button[type=submit]").click();

        // On attend d'être sûr d'être connecté avant de rendre la main au test
        // C'est important pour la stabilité
        await expect(page.locator("div.sia-panel-title")).toBeVisible();
    });

    // TEST 1 : Puisqu'on est déjà connecté via le beforeEach,
    // on vérifie juste que l'interface est correcte.
    test('La page d\'accueil est affichée correctement après connexion', async ({ page }) => {
        // Le travail est déjà fait par le beforeEach
        // On peut faire une assertion supplémentaire si besoin
        await expect(page).toHaveTitle(/Siamois/);
    });

    // TEST 2 : Le beforeEach nous a connecté, donc on peut tester la déconnexion directement
    test('Doit pouvoir se déconnecter', async ({ page }) => {
        // Pas besoin de code de connexion ici !

        // Action : Déconnexion
        await page.locator('button.logout-button').click();
        await page.locator("button.ui-confirmdialog-yes").click();

        // Vérification
        await expect(page.getByText("Bienvenue sur Siamois")).toBeVisible();
    });

});