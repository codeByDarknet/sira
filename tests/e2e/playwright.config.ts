import { defineConfig, devices } from '@playwright/test';

/**
 * Quatre groupes de scénarios :
 *  - « ossature » : identité, messagerie et téléphone simulé ; ne demande que Keycloak, Mailpit,
 *    le proxy d'entrée et l'écran du téléphone ;
 *  - « parcours » : les parcours clés (accord, minimisation, panne, console), contre la pile de démonstration (`scripts/sira demo`) ;
 *  - « socle » : contre le socle seul (`scripts/sira demarrer`), sans rien de la démonstration ;
 *  - « etranger » : la preuve de raccordement d'un registre d'une autre équipe (`scripts/sira etranger`).
 *
 * Les scénarios partagent l'état de la pile (comptes, registres mis en panne) : ils se suivent,
 * un seul à la fois.
 */
export default defineConfig({
  testDir: './scenarios',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    ...devices['Desktop Chrome'],
    locale: 'fr-FR',
    timezoneId: 'Africa/Ouagadougou',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'ossature', testMatch: /ossature\/.*\.spec\.ts/ },
    { name: 'parcours', testMatch: /parcours\/.*\.spec\.ts/ },
    // Socle seul (`scripts/sira demarrer`) : ce que la démonstration ajoute n'y est pas servi.
    { name: 'socle', testMatch: /socle\/.*\.spec\.ts/ },
    // Registre d'une autre équipe (profil compose « etranger ») : lancé par `scripts/sira etranger`.
    { name: 'etranger', testMatch: /etranger\/.*\.spec\.ts/ },
  ],
});
