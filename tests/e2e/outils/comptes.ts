import { execFileSync } from 'node:child_process';
import { createHmac } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

export type RoleSocle = 'instructeur' | 'autorite' | 'auditeur';

/** Citoyens fictifs de `identite/demo/sira-citoyens-users-0.json`. */
export const CITOYENS = {
  aicha: { identifiant: '990048217350', nom: 'Wendkuni Aïcha KABORÉ' },
  bakary: { identifiant: '990017734062', nom: 'Bakary Désiré SANOU' },
  rasmata: { identifiant: '990063390218', nom: 'Pingdwendé Rasmata ZONGO' },
  dieudonne: { identifiant: '990029915477', nom: 'Dieudonné Yirbar SOMÉ' },
  mariam: { identifiant: '990085502641', nom: 'Mariam Sita COULIBALY' },
  hamidou: { identifiant: '990051176839', nom: 'Hamidou Boureima DICKO' },
} as const;

export type Citoyen = (typeof CITOYENS)[keyof typeof CITOYENS];

/** Date de naissance tenue par le registre d'état civil : celle qu'un service ne doit pas voir. */
export function dateNaissance(citoyen: Citoyen): string {
  const fichier = resolve(__dirname, '../../../registres/donnees/etat-civil/personnes.json');
  const { personnes } = JSON.parse(readFileSync(fichier, 'utf8')) as {
    personnes: { identifiant_unique: string; date_naissance: string }[];
  };
  const personne = personnes.find((p) => p.identifiant_unique === citoyen.identifiant);
  if (!personne) {
    throw new Error(`${citoyen.nom} est absent du registre d'état civil de démonstration.`);
  }
  return personne.date_naissance;
}

const SCRIPT = resolve(__dirname, '../../../scripts/sira');

/** Lance `scripts/sira` ; sous Windows, Node ne sait pas exécuter un script shell sans `bash`. */
function sira(...arguments_: string[]): string {
  return process.platform === 'win32'
    ? execFileSync('bash', [SCRIPT, ...arguments_], { encoding: 'utf8' })
    : execFileSync(SCRIPT, arguments_, { encoding: 'utf8' });
}

/**
 * Secrets des comptes de démonstration, lus au moment de s'en servir par `scripts/sira comptes`.
 * Ils sont générés au premier démarrage de la pile : ni le dépôt ni ces tests ne les contiennent.
 */
function secretsDemo(): Map<string, string> {
  const sortie = sira('comptes');
  const secrets = new Map<string, string>();
  for (const ligne of sortie.split('\n')) {
    const [nom, ...reste] = ligne.trim().split(/\s+/);
    if (nom?.startsWith('demo-') && reste.length > 0) {
      secrets.set(nom, reste[reste.length - 1]);
    }
  }
  return secrets;
}

function secret(nom: string): string {
  const valeur = secretsDemo().get(nom);
  if (!valeur) {
    throw new Error(`« scripts/sira comptes » ne donne pas ${nom} : la pile est-elle démarrée ?`);
  }
  return valeur;
}

/** Mot de passe commun aux administrateurs et aux agents de démonstration. */
export function motDePasseDemo(): string {
  return secret('demo-mot-de-passe');
}

/**
 * Clés TOTP des administrateurs enrôlés par ces tests. Comme une vraie personne, un test ajoute
 * l'application à la première connexion : Keycloak montre alors un QR code et la clé en clair,
 * une seule fois. La clé est gardée ici (dossier ignoré par Git) pour les connexions suivantes.
 */
const DOSSIER_CLES = resolve(__dirname, '../.totp');

function fichierCle(role: RoleSocle): string {
  return resolve(DOSSIER_CLES, `${role}.txt`);
}

export function cleTotp(role: RoleSocle): string | undefined {
  return existsSync(fichierCle(role)) ? readFileSync(fichierCle(role), 'utf8').trim() : undefined;
}

export function enregistrerCleTotp(role: RoleSocle, cle: string): void {
  mkdirSync(DOSSIER_CLES, { recursive: true });
  writeFileSync(fichierCle(role), cle.replace(/\s+/g, ''), { mode: 0o600 });
}

/**
 * Efface l'application d'authentification du compte (`scripts/sira totp-reinitialiser`) : la
 * prochaine connexion repasse par l'écran du QR code. Utile quand la clé gardée ici manque ou ne
 * correspond plus (pile réinitialisée, compte ajouté à la main sur un téléphone).
 */
export function reinitialiserTotp(role: RoleSocle): void {
  sira('totp-reinitialiser', role);
  rmSync(fichierCle(role), { force: true });
}

function base32(cle: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const caractere of cle.replace(/\s+/g, '').toUpperCase()) {
    bits += alphabet.indexOf(caractere).toString(2).padStart(5, '0');
  }
  const octets: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) {
    octets.push(parseInt(bits.slice(i, i + 8), 2));
  }
  return Buffer.from(octets);
}

/** Code TOTP valable maintenant pour cette clé (RFC 6238 : HMAC-SHA1, 6 chiffres, 30 s). */
export function codeTotp(cle: string, instant = Date.now()): string {
  const compteur = Buffer.alloc(8);
  compteur.writeBigUInt64BE(BigInt(Math.floor(instant / 30_000)));
  const mac = createHmac('sha1', base32(cle)).update(compteur).digest();
  const decalage = mac[mac.length - 1] & 0x0f;
  const tronque = mac.readUInt32BE(decalage) & 0x7fffffff;
  return String(tronque % 1_000_000).padStart(6, '0');
}
