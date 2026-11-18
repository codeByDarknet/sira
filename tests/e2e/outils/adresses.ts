/** Adresses publiques de la pile : un seul port (SIRA_PORT), un nom d'hôte par écran. */
const PORT = process.env.SIRA_PORT ?? '8600';

const ecran = (nom: string) => `http://${nom}.sira.localhost:${PORT}`;

export const ADRESSES = {
  identite: ecran('identite'),
  citoyen: ecran('citoyen'),
  console: ecran('console'),
  concours: ecran('concours'),
  bourses: ecran('bourses'),
  entreprises: ecran('entreprises'),
  etatCivil: ecran('etat-civil'),
  nationalite: ecran('nationalite'),
  casier: ecran('casier'),
  diplomes: ecran('diplomes'),
  // Le téléphone simulé peut être servi ailleurs pendant sa mise au point.
  telephone: process.env.SIRA_TELEPHONE ?? ecran('telephone'),
} as const;

export type Registre = 'etatCivil' | 'nationalite' | 'casier' | 'diplomes';

/** Vrai si l'adresse courante de la page appartient à cet écran. */
export function sur(adresse: string): (url: URL) => boolean {
  return (url) => url.origin === new URL(adresse).origin;
}
