import { Cookie, Page, expect } from '@playwright/test';
import { ADRESSES, Registre, sur } from './adresses';
import {
  RoleSocle,
  cleTotp,
  codeTotp,
  enregistrerCleTotp,
  motDePasseDemo,
  reinitialiserTotp,
} from './comptes';
import { attendreCode } from './messagerie';

/**
 * Part vers la connexion d'une application par sa route documentée (API.md), puis attend
 * l'écran du fournisseur d'identité, ou le retour immédiat si la session y est encore ouverte.
 */
async function ouvrirConnexion(page: Page, application: string, retour: string): Promise<boolean> {
  await page.goto(`${application}/api/auth/connexion/sira?retour=${encodeURIComponent(retour)}`);
  await page.waitForURL((url) => sur(ADRESSES.identite)(url) || sur(application)(url));
  return sur(ADRESSES.identite)(new URL(page.url()));
}

/** Écran de connexion du citoyen : identifiant unique, puis code reçu sur son téléphone. */
export async function saisirConnexionCitoyen(page: Page, identifiant: string): Promise<void> {
  await page.getByLabel('Identifiant unique').fill(identifiant);
  const code = await attendreCode(identifiant, () =>
    page.getByRole('button', { name: 'Continuer' }).click(),
  );
  await page.getByLabel('Code à 6 chiffres reçu par message').fill(code);
  await page.getByRole('button', { name: 'Valider' }).click();
}

/**
 * Écran de connexion d'un administrateur du socle : mot de passe, puis code TOTP, comme pour une
 * vraie personne. À la première connexion (ou après `scripts/sira totp-reinitialiser`), Keycloak
 * montre le QR code et la clé une seule fois : le test lit la clé, la garde, et donne le premier
 * code. Sans clé gardée, le compte est d'abord réinitialisé. Un code TOTP ne sert qu'une fois :
 * s'il est refusé (déjà utilisé, ou période qui change pendant la saisie), le suivant est attendu.
 */
export async function saisirConnexionAdministrateur(
  page: Page,
  role: RoleSocle,
  reessayer = true,
): Promise<void> {
  if (!cleTotp(role)) {
    reinitialiserTotp(role);
  }
  await page.getByLabel('Adresse électronique').fill(`${role}@sira.example`);
  await page.getByLabel('Mot de passe', { exact: true }).fill(motDePasseDemo());
  await page.getByRole('button', { name: 'Continuer' }).click();

  const cleAffichee = page.locator('#kc-totp-secret-key');
  await page.locator('#kc-totp-secret-key, #otp').first().waitFor();
  let cle: string;
  let bouton: string;
  if (await cleAffichee.isVisible()) {
    cle = (await cleAffichee.textContent()) ?? '';
    enregistrerCleTotp(role, cle);
    bouton = 'Valider';
  } else {
    const gardee = cleTotp(role);
    if (!gardee) {
      throw new Error(`aucune clé TOTP gardée pour ${role}`);
    }
    cle = gardee;
    bouton = 'Continuer';
  }

  const champ = page.locator('#totp, #otp').first();
  let dernier = '';
  let refus = 0;
  if (bouton === 'Continuer' && reessayer) {
    // Clé gardée qui ne correspond plus (pile réinitialisée, compte ajouté sur un vrai téléphone) :
    // deux codes successifs refusés, on réinitialise le compte et on recommence la connexion.
    try {
      await expect(async () => {
        const code = codeTotp(cle);
        expect(code, 'un nouveau code TOTP est disponible').not.toBe(dernier);
        dernier = code;
        await champ.fill(code);
        await page.getByRole('button', { name: bouton }).click();
        if (!(await champ.isHidden({ timeout: 5_000 }).catch(() => false))) {
          refus += 1;
        }
        await expect(champ).toBeHidden({ timeout: 5_000 });
      }).toPass({ timeout: 75_000, intervals: [5_000] });
      return;
    } catch (erreur) {
      if (refus < 2) {
        throw erreur;
      }
      reinitialiserTotp(role);
      await page.getByRole('link', { name: 'Changer de compte' }).click();
      return saisirConnexionAdministrateur(page, role, false);
    }
  }
  await expect(async () => {
    const code = codeTotp(cle);
    expect(code, 'un nouveau code TOTP est disponible').not.toBe(dernier);
    dernier = code;
    await champ.fill(code);
    await page.getByRole('button', { name: bouton }).click();
    await expect(champ).toBeHidden({ timeout: 5_000 });
  }).toPass({ timeout: 75_000, intervals: [5_000] });
}

/** Écran de connexion d'un agent d'une administration simulée : mot de passe seul. */
export async function saisirConnexionAgent(page: Page, administration: string): Promise<void> {
  await page.getByLabel('Adresse électronique').fill(`agent.${administration}@sira.example`);
  await page.getByLabel('Mot de passe', { exact: true }).fill(motDePasseDemo());
  await page.getByRole('button', { name: 'Continuer' }).click();
}

/**
 * Session ouverte au fournisseur d'identité par chaque citoyen pendant ce passage. Un identifiant
 * ne reçoit que dix codes par heure : la suite n'en demande qu'un par citoyen, et reste rejouable.
 */
const SESSIONS_CITOYENS = new Map<string, Cookie[]>();

/** Ouvre l'espace citoyen, connecté, sur l'écran `retour`. */
export async function connecterCitoyen(
  page: Page,
  identifiant: string,
  retour = '/',
): Promise<void> {
  const contexte = page.context();
  await contexte.addCookies(SESSIONS_CITOYENS.get(identifiant) ?? []);
  if (await ouvrirConnexion(page, ADRESSES.citoyen, retour)) {
    await saisirConnexionCitoyen(page, identifiant);
  }
  await page.waitForURL(sur(ADRESSES.citoyen));
  const hote = new URL(ADRESSES.identite).hostname;
  SESSIONS_CITOYENS.set(
    identifiant,
    (await contexte.cookies()).filter((cookie) => cookie.domain === hote),
  );
}

/** Ouvre la console du socle, connecté avec ce rôle, sur l'écran `retour`. */
export async function connecterAdministrateur(
  page: Page,
  role: RoleSocle,
  retour = '/',
): Promise<void> {
  if (await ouvrirConnexion(page, ADRESSES.console, retour)) {
    await saisirConnexionAdministrateur(page, role);
  }
  await page.waitForURL(sur(ADRESSES.console));
}

const CODES_REGISTRE: Record<Registre, string> = {
  etatCivil: 'etat-civil',
  nationalite: 'nationalite',
  casier: 'casier',
  diplomes: 'diplomes',
};

/** Ouvre le back-office d'un registre simulé, connecté comme son agent, sur l'écran `retour`. */
export async function connecterAgentRegistre(
  page: Page,
  registre: Registre,
  retour = '/',
): Promise<void> {
  if (await ouvrirConnexion(page, ADRESSES[registre], retour)) {
    await saisirConnexionAgent(page, CODES_REGISTRE[registre]);
  }
  await page.waitForURL(sur(ADRESSES[registre]));
}

/**
 * Ouvre le back-office d'un service simulé (`concours`, `bourses`, `entreprises`), connecté comme
 * son agent.
 */
export async function connecterAgentService(
  page: Page,
  service: 'concours' | 'bourses' | 'entreprises',
  retour = '/',
): Promise<void> {
  if (await ouvrirConnexion(page, ADRESSES[service], retour)) {
    await saisirConnexionAgent(page, service);
  }
  await page.waitForURL(sur(ADRESSES[service]));
}
