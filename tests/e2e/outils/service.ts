import { Page, expect } from '@playwright/test';
import { ADRESSES } from './adresses';
import { Citoyen } from './comptes';
import { connecterCitoyen } from './connexion';
import { appeler } from './navigateur';

export type Service = 'concours' | 'bourses' | 'entreprises';
export type Canal = 'REDIRECTION' | 'CODE' | 'NOTIFICATION';

/** La demande telle que le service simulé la rend à l'écran d'attente (`/api/parcours/demande`). */
export interface Demande {
  id: string;
  etat: string;
  url_accord: string | null;
  code_court: string | null;
  code_verification: string | null;
  dossier_ouvert: boolean;
}

export interface Verification {
  jeu: string;
  libelle: string;
  niveau: string;
  etat: string;
  valeur: boolean | null;
  champs: Record<string, unknown> | null;
  piece: { nom: string; type: string; taille: number } | null;
}

/** Le dossier tel que le service simulé le rend (`/api/parcours/dossier`). */
export interface Dossier {
  numero: string;
  etat: 'EN_VERIFICATION' | 'COMPLET' | 'A_EXAMINER';
  verifications: Verification[];
}

// Libellés du portail de service (`interfaces/demo/projects/portail-service`).
const COMMENCER = 'Commencer';
const IDENTIFIANT = 'Votre identifiant unique';
const SUIVANT = 'Suivant';
export const MODALE = 'Ce que nous allons récupérer';
const AUTRES_FACONS = 'Autres façons de donner mon accord';
const CHOIX: Record<Canal, string> = {
  REDIRECTION: 'Continuer avec Sira',
  CODE: 'Afficher le code',
  NOTIFICATION: 'Recevoir la demande',
};
const CONTINUER = 'Continuer';
const DEPOSER = 'Déposer mon dossier';
export const DEPOSE = 'Votre dossier est déposé';

/** Les déclarations et le dépôt, en plus de ce que le dossier porte déjà (`/api/parcours/dossier`). */
export interface DossierComplet extends Dossier {
  depose_le: string | null;
  declarations: {
    code: string;
    nature: 'TEXTE' | 'PIECE';
    remplace: boolean;
    valeur: string | null;
    piece: { nom: string; type: string; taille: number } | null;
  }[];
}

/**
 * Remplit « Votre demande » : l'identifiant unique saisi par la personne, puis « Suivant ». La
 * modale dit ce qui sera récupéré et auprès de quel registre.
 */
export async function remplirDemande(
  page: Page,
  service: Service,
  identifiant: string,
): Promise<void> {
  await page.goto(ADRESSES[service]);
  await page.getByRole('link', { name: COMMENCER, exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Votre demande' })).toBeVisible();
  await page.getByLabel(IDENTIFIANT).fill(identifiant);
  await page.getByRole('button', { name: SUIVANT }).click();
  await expect(page.getByRole('dialog', { name: MODALE })).toBeVisible();
}

/**
 * Commence la démarche sur le portail du service avec l'identifiant unique saisi dans le
 * formulaire, puis choisit la façon de donner son accord : « Continuer avec Sira » dans la modale,
 * ou l'un des autres canaux. Rend la réponse du service à la création de la demande.
 */
export async function ouvrirDemande(
  page: Page,
  service: Service,
  canal: Canal,
  identifiant: string,
): Promise<Demande> {
  await remplirDemande(page, service, identifiant);
  // La réponse est lue au passage : par l'espace Sira, la page la quitte aussitôt pour l'accord.
  const adresse = `${ADRESSES[service]}/api/parcours/demandes`;
  let creation: { statut: number; corps: Demande } | undefined;
  await page.route(adresse, async (route) => {
    const reponse = await route.fetch();
    if (route.request().method() === 'POST') {
      creation = { statut: reponse.status(), corps: (await reponse.json()) as Demande };
    }
    await route.fulfill({ response: reponse });
  });
  const modale = page.getByRole('dialog', { name: MODALE });
  if (canal === 'REDIRECTION') {
    await modale.getByRole('button', { name: CHOIX.REDIRECTION, exact: true }).click();
  } else {
    await modale.getByRole('link', { name: AUTRES_FACONS }).click();
    await page.getByRole('button', { name: CHOIX[canal], exact: true }).click();
  }
  await expect.poll(() => creation?.statut, { message: 'le service crée la demande' }).toBe(201);
  await page.unroute(adresse);
  return (creation as { corps: Demande }).corps;
}

/**
 * Après l'accord, l'écran « Récupération de vos informations » suit chaque registre ; « Continuer »
 * mène au formulaire prérempli. Rend le dossier lu à ce moment.
 */
export async function ouvrirFormulaire(page: Page, service: Service): Promise<DossierComplet> {
  await page.waitForURL((url) => url.origin === ADRESSES[service]);
  if (new URL(page.url()).pathname !== '/dossier') {
    await expect(
      page.getByRole('heading', { level: 1, name: 'Récupération de vos informations' }),
    ).toBeVisible({ timeout: 30_000 });
    await page.getByRole('button', { name: CONTINUER, exact: true }).click();
  }
  await expect(page.getByRole('heading', { level: 1, name: 'Votre dossier' })).toBeVisible();
  return (await appeler<DossierComplet>(page, '/api/parcours/dossier')).corps;
}

/** « Déposer mon dossier » depuis le formulaire prérempli ; rend le dossier déposé. */
export async function deposer(page: Page, service: Service): Promise<DossierComplet> {
  await ouvrirFormulaire(page, service);
  await page.getByRole('button', { name: DEPOSER }).click();
  await expect(page.getByRole('heading', { level: 1, name: DEPOSE })).toBeVisible();
  const dossier = (await appeler<DossierComplet>(page, '/api/parcours/dossier')).corps;
  expect(dossier.depose_le, 'le dossier porte sa date de dépôt').toBeTruthy();
  return dossier;
}

export async function lireDemande(page: Page): Promise<Demande> {
  return (await appeler<Demande>(page, '/api/parcours/demande')).corps;
}

/** Attend que le service ouvre le dossier de la session, puis le rend. */
export async function attendreDossier(page: Page, service: Service): Promise<Dossier> {
  await page.waitForURL((url) => url.origin === ADRESSES[service]);
  let dossier: Dossier | undefined;
  await expect(async () => {
    const reponse = await appeler<Dossier>(page, '/api/parcours/dossier');
    expect(reponse.statut, 'le dossier est ouvert').toBe(200);
    dossier = reponse.corps;
  }).toPass({ timeout: 30_000 });
  return dossier as Dossier;
}

/**
 * Écran d'accord de l'espace citoyen : le citoyen lit la demande, puis répond. Par code court ou
 * par notification, autoriser exige de recopier le code à 6 chiffres affiché par le service.
 */
export async function repondre(
  page: Page,
  reponse: 'Autoriser' | 'Refuser',
  codeService?: string,
): Promise<void> {
  await expect(
    page.getByRole('heading', { name: 'Un service demande vos informations' }),
  ).toBeVisible();
  if (codeService) {
    await page.getByLabel('Code affiché par le service').fill(codeService);
  }
  await page.getByRole('button', { name: reponse, exact: true }).click();
}

/** Le code à 6 chiffres que le service affiche à côté du code court ou de la notification. */
export async function codeDuService(page: Page): Promise<string> {
  const code = (await lireDemande(page)).code_verification;
  expect(code, 'le service affiche un code de vérification').toMatch(/^\d{6}$/);
  return code as string;
}

/**
 * Le parcours le plus court : le citoyen, connecté à son espace, saisit son identifiant dans
 * « Votre demande », est envoyé sur l'écran d'accord, autorise, revient chez le service, suit la
 * récupération et arrive sur le formulaire prérempli (« Votre dossier », pas encore déposé).
 */
export async function inscrireParRedirection(
  page: Page,
  service: Service,
  citoyen: Citoyen,
): Promise<Dossier> {
  await connecterCitoyen(page, citoyen.identifiant);
  await ouvrirDemande(page, service, 'REDIRECTION', citoyen.identifiant);
  await attendreEcranAccord(page);
  await repondre(page, 'Autoriser');
  const dossier = await attendreDossier(page, service);
  await ouvrirFormulaire(page, service);
  return dossier;
}

/** Attend l'écran d'accord de l'espace citoyen, où le navigateur est envoyé par le service. */
export async function attendreEcranAccord(page: Page): Promise<void> {
  await page.waitForURL(
    (url) => url.origin === ADRESSES.citoyen && /^\/accord\//.test(url.pathname),
  );
}

export function verification(dossier: Dossier, jeu: string): Verification {
  const trouvee = dossier.verifications.find((v) => v.jeu === jeu);
  expect(trouvee, `le dossier porte la vérification ${jeu}`).toBeDefined();
  return trouvee as Verification;
}
