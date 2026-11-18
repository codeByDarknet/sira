// Import d'une liste de citoyens fictifs dans la démonstration : registres simulés et comptes
// Keycloak (royaume sira-citoyens). Appelé par « scripts/sira citoyens importer », qui fournit
// COMPOSE_FILE ; format décrit dans docs/composants/demo-registres.md.
//
//   node scripts/citoyens.mjs <fichier .json|.csv> [--correspondance <fichier.json>] [--essai]
//
// Idempotent : une personne déjà présente dans un registre n'est pas touchée (une correction faite
// par un agent survit), un compte déjà présent est gardé. Tout ou rien : à la moindre ligne
// invalide, rien n'est écrit.
import { spawnSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DEPLOIEMENT = join(RACINE, 'deploiement');
const DONNEES = join(RACINE, 'registres', 'donnees');
// Champs d'identité communs : ils seuls ne font pas entrer une personne dans un registre.
const IDENTITE = ['identifiant_unique', 'nom', 'prenoms', 'date_naissance', 'lieu_naissance'];
const MAX_PERSONNES = 10_000;

function arreter(message) {
  console.error(`citoyens : ${message}`);
  process.exit(1);
}

// --- Arguments -----------------------------------------------------------------------------
const args = process.argv.slice(2);
let fichier;
let correspondance = {};
let essai = false;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--essai') essai = true;
  else if (args[i] === '--correspondance') correspondance = lireCorrespondance(args[++i]);
  else if (!fichier) fichier = args[i];
  else arreter(`argument inattendu : ${args[i]}`);
}
if (!fichier) arreter('usage : scripts/sira citoyens importer <fichier> [--correspondance <fichier.json>] [--essai]');

function lireCorrespondance(chemin) {
  if (!chemin) arreter('--correspondance attend un fichier JSON');
  const table = JSON.parse(readFileSync(chemin, 'utf8'));
  if (typeof table !== 'object' || Array.isArray(table) || table === null) {
    arreter('la correspondance est un objet JSON { "colonne du fichier": "champ de Sira" }');
  }
  return table;
}

// --- Lecture du fichier ---------------------------------------------------------------------
/** CSV (RFC 4180) : séparateur « , » ou « ; » deviné sur l'en-tête, guillemets doublés. */
function lireCsv(texte) {
  const separateur = (texte.split('\n')[0].match(/;/g) ?? []).length > (texte.split('\n')[0].match(/,/g) ?? []).length ? ';' : ',';
  const lignes = [];
  let ligne = [];
  let champ = '';
  let entreGuillemets = false;
  for (let i = 0; i < texte.length; i++) {
    const c = texte[i];
    if (entreGuillemets) {
      if (c === '"' && texte[i + 1] === '"') { champ += '"'; i++; }
      else if (c === '"') entreGuillemets = false;
      else champ += c;
    } else if (c === '"') entreGuillemets = true;
    else if (c === separateur) { ligne.push(champ); champ = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && texte[i + 1] === '\n') i++;
      ligne.push(champ); champ = '';
      if (ligne.some((v) => v !== '')) lignes.push(ligne);
      ligne = [];
    } else champ += c;
  }
  ligne.push(champ);
  if (ligne.some((v) => v !== '')) lignes.push(ligne);
  const [entete, ...corps] = lignes;
  return corps.map((valeurs) => Object.fromEntries(entete.map((nom, j) => [nom.trim(), valeurs[j] ?? ''])));
}

function lirePersonnes(chemin) {
  const texte = readFileSync(chemin, 'utf8').replace(/^﻿/, '');
  if (extname(chemin).toLowerCase() === '.csv') return lireCsv(texte);
  const lu = JSON.parse(texte);
  const liste = Array.isArray(lu) ? lu : lu.personnes;
  if (!Array.isArray(liste)) arreter('le JSON est une liste, ou un objet { "personnes": [...] }');
  return liste;
}

// --- Description des registres (registres/donnees/<code>/registre.yaml) ----------------------
// Lecture volontairement limitée à la forme de ces fichiers : liste « champs », une entrée par
// ligne « - { cle: valeur, ... } » ou en bloc « - nom: ... » suivi de lignes indentées.
function paires(texte) {
  const objet = {};
  let profondeur = 0;
  let morceau = '';
  const morceaux = [];
  for (const c of texte) {
    if (c === '{') profondeur++;
    if (c === '}') profondeur--;
    if (c === ',' && profondeur === 0) { morceaux.push(morceau); morceau = ''; }
    else morceau += c;
  }
  morceaux.push(morceau);
  for (const m of morceaux) {
    const i = m.indexOf(':');
    if (i > 0) objet[m.slice(0, i).trim()] = valeur(m.slice(i + 1));
  }
  return objet;
}

function valeur(brute) {
  const v = brute.trim();
  if (v.startsWith('{') && v.endsWith('}')) return paires(v.slice(1, -1));
  if (v === 'true' || v === 'false') return v === 'true';
  if (/^-?\d+(\.\d+)?$/.test(v)) return Number(v);
  return v;
}

function lireChamps(texte) {
  const champs = [];
  let dans = false;
  let courant;
  for (const brute of texte.split('\n')) {
    const ligne = brute.replace(/(^|\s)#.*$/, '');
    if (/^\s*champs:\s*$/.test(ligne)) { dans = true; continue; }
    if (!dans || !ligne.trim()) continue;
    if (/^\s{0,4}\w+:/.test(ligne) && !/^\s*-/.test(ligne) && !courant) break;
    if (/^\s*documents:/.test(ligne)) break;
    let m = /^\s*- \{(.*)\}\s*$/.exec(ligne);
    if (m) { champs.push((courant = paires(m[1]))); continue; }
    m = /^\s*- (\w+):(.*)$/.exec(ligne);
    if (m) { champs.push((courant = { [m[1]]: valeur(m[2]) })); continue; }
    m = /^\s+(\w+):(.*)$/.exec(ligne);
    if (m && courant) courant[m[1]] = valeur(m[2]);
  }
  return champs;
}

const REGISTRES = readdirSync(DONNEES, { withFileTypes: true })
  .filter((d) => d.isDirectory())
  .map((d) => ({ code: d.name, champs: lireChamps(readFileSync(join(DONNEES, d.name, 'registre.yaml'), 'utf8')) }));
for (const r of REGISTRES) {
  if (!r.champs.some((c) => c.nom === 'identifiant_unique')) arreter(`description illisible : ${r.code}`);
}

// --- Contrôle et mise en forme ----------------------------------------------------------------
function convertir(champ, brute) {
  const texte = typeof brute === 'string' ? brute.trim() : brute;
  switch (champ.type) {
    case 'DATE': {
      const d = String(texte);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(d) || new Date(`${d}T00:00:00Z`).toISOString().slice(0, 10) !== d) {
        throw new Error('date attendue sous la forme AAAA-MM-JJ');
      }
      return d;
    }
    case 'NOMBRE': {
      const n = typeof texte === 'number' ? texte : Number(String(texte).replace(',', '.'));
      if (!Number.isFinite(n)) throw new Error('nombre attendu');
      if ((champ.minimum !== undefined && n < champ.minimum) || (champ.maximum !== undefined && n > champ.maximum)) {
        throw new Error(`hors des bornes ${champ.minimum} à ${champ.maximum}`);
      }
      return n;
    }
    case 'BOOLEEN': {
      const b = String(texte).toLowerCase();
      if (['true', 'oui', '1', 'vrai'].includes(b)) return true;
      if (['false', 'non', '0', 'faux'].includes(b)) return false;
      throw new Error('oui ou non attendu');
    }
    case 'CHOIX':
      if (!Object.hasOwn(champ.choix ?? {}, String(texte))) {
        throw new Error(`une valeur parmi ${Object.keys(champ.choix ?? {}).join(', ')}`);
      }
      return String(texte);
    default: {
      const t = String(texte);
      if (t.length > 500) throw new Error('500 caractères au plus');
      return t;
    }
  }
}

/** Rend les fiches de cette personne, par registre ; lève une erreur lisible sinon. */
function fiches(personne) {
  const id = String(personne.identifiant_unique ?? '').trim();
  if (!/^[0-9]{12}$/.test(id)) throw new Error('identifiant_unique : 12 chiffres attendus');
  const resultat = {};
  for (const registre of REGISTRES) {
    const propres = registre.champs.filter((c) => !IDENTITE.includes(c.nom));
    const present = (nom) => personne[nom] !== undefined && personne[nom] !== null && String(personne[nom]).trim() !== '';
    // L'état civil reçoit tout le monde ; un autre registre, ceux qui portent l'un de ses champs.
    if (registre.code !== 'etat-civil' && !propres.some((c) => present(c.nom))) continue;
    const fiche = {};
    for (const champ of registre.champs) {
      if (!present(champ.nom)) {
        if (!champ.facultatif) throw new Error(`${registre.code} : ${champ.nom} manque`);
        continue;
      }
      try {
        fiche[champ.nom] = convertir(champ, personne[champ.nom]);
      } catch (e) {
        throw new Error(`${registre.code} : ${champ.nom} : ${e.message}`);
      }
    }
    resultat[registre.code] = fiche;
  }
  return { id, fiches: resultat };
}

/** Comme le registre simulé (DepotPersonnes.normaliser) : « kabore » trouve « KABORÉ ». */
const normaliser = (t) => t.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim();

const brutes = lirePersonnes(fichier);
if (brutes.length === 0) arreter('aucune personne dans le fichier');
if (brutes.length > MAX_PERSONNES) arreter(`${MAX_PERSONNES} personnes au plus par import`);
const erreurs = [];
const vus = new Set();
const personnes = brutes.map((brute, i) => {
  const personne = {};
  for (const [colonne, v] of Object.entries(brute ?? {})) {
    personne[correspondance[colonne] ?? colonne] = v;
  }
  try {
    const p = fiches(personne);
    if (vus.has(p.id)) throw new Error('identifiant en double dans le fichier');
    vus.add(p.id);
    return p;
  } catch (e) {
    erreurs.push(`  entrée ${i + 1} : ${e.message}`);
    return null;
  }
});
if (erreurs.length > 0) {
  console.error(`citoyens : ${erreurs.length} entrée(s) refusée(s), rien n'a été écrit :`);
  console.error(erreurs.slice(0, 50).join('\n'));
  process.exit(1);
}
for (const r of REGISTRES) {
  const n = personnes.filter((p) => p.fiches[r.code]).length;
  console.log(`citoyens : ${r.code} : ${n} fiche(s) prête(s)`);
}
if (essai) {
  console.log('citoyens : essai seulement, rien n\'a été écrit');
  process.exit(0);
}

// --- Écriture -----------------------------------------------------------------------------
function compose(arguments_, entree) {
  const r = spawnSync('docker', ['compose', ...arguments_], {
    cwd: DEPLOIEMENT,
    input: entree,
    encoding: 'utf8',
    env: process.env,
  });
  return { code: r.status, sortie: (r.stdout ?? '').trim(), erreur: (r.stderr ?? '').trim() };
}

const csv = (v) => `"${String(v).replaceAll('"', '""')}"`;

// Registres : les données passent par COPY, jamais dans le texte d'une requête SQL.
for (const r of REGISTRES) {
  const lignes = personnes
    .filter((p) => p.fiches[r.code])
    .map((p) => {
      const f = p.fiches[r.code];
      return [p.id, JSON.stringify(f), normaliser(`${f.nom} ${f.prenoms} ${p.id}`)].map(csv).join(',');
    });
  if (lignes.length === 0) continue;
  const script = [
    'CREATE TEMP TABLE import (identifiant text, fiche jsonb, recherche text);',
    'COPY import FROM STDIN WITH (FORMAT csv);',
    ...lignes,
    '\\.',
    'WITH ajout AS (INSERT INTO personne (identifiant, fiche, recherche)',
    '  SELECT identifiant, fiche, recherche FROM import',
    '  ON CONFLICT (identifiant) DO NOTHING RETURNING 1)',
    'SELECT count(*) FROM ajout;',
    '',
  ].join('\n');
  const base = `registre_${r.code.replaceAll('-', '_')}`;
  const res = compose(['exec', '-T', 'demo-db', 'psql', '--username', 'postgres', '--dbname', base,
    '--no-psqlrc', '--quiet', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1'], script);
  if (res.code !== 0) arreter(`${r.code} : écriture refusée\n${res.erreur}`);
  console.log(`citoyens : ${r.code} : ${res.sortie} ajoutée(s), ${lignes.length - Number(res.sortie)} déjà présente(s)`);
}

// Comptes : outil d'administration de Keycloak, dans son conteneur, avec le compte d'amorçage.
const KCADM = ['exec', '-T', 'keycloak', '/opt/keycloak/bin/kcadm.sh'];
const CONFIG = ['--config', '/tmp/sira-import.kcadm'];
const session = compose(['exec', '-T', 'keycloak', 'sh', '-c',
  'exec /opt/keycloak/bin/kcadm.sh config credentials --config /tmp/sira-import.kcadm '
  + '--server http://127.0.0.1:8080 --realm master --user amorcage '
  + '--password "$(cat /run/sira/secrets/amorcage.motdepasse)"']);
if (session.code !== 0) arreter(`connexion à Keycloak refusée\n${session.erreur}`);
let crees = 0;
let presents = 0;
try {
  for (const p of personnes) {
    const etat = p.fiches['etat-civil'];
    const compte = {
      username: p.id,
      enabled: true,
      firstName: etat.prenoms,
      lastName: etat.nom,
      email: `${p.id}@telephone.sira.example`,
      emailVerified: true,
      attributes: { iue: [p.id] },
    };
    const res = compose([...KCADM, 'create', 'users', ...CONFIG, '-r', 'sira-citoyens', '-f', '-'], JSON.stringify(compte));
    if (res.code === 0) crees++;
    else if (/exists/i.test(res.erreur)) presents++;
    else arreter(`compte ${p.id} refusé\n${res.erreur}`);
  }
} finally {
  compose(['exec', '-T', 'keycloak', 'rm', '-f', '/tmp/sira-import.kcadm']);
}
console.log(`citoyens : comptes : ${crees} créé(s), ${presents} déjà présent(s)`);
