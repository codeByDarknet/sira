import { InjectionToken } from '@angular/core';

/**
 * Configuration lue au démarrage dans `/config.json`. Le même conteneur sert plusieurs
 * plateformes : seuls le nom et le thème changent, fixés par les variables `CONFIG_*`.
 */
export interface ConfigurationDemo {
  /** Nom affiché dans l'en-tête et dans le titre des pages. */
  nom: string;
  /** Ligne sous le nom : l'organisme qui rend le service. */
  organisme: string;
  /** Thème de couleurs, parmi `THEMES`. */
  theme: Theme;
}

export const THEMES = [
  'socle',
  'console',
  'concours',
  'bourses',
  'entreprises',
  'etat-civil',
  'nationalite',
  'casier',
  'diplomes',
] as const;
export type Theme = (typeof THEMES)[number];

export const CONFIGURATION = new InjectionToken<ConfigurationDemo>('CONFIGURATION');

/**
 * Lit `/config.json`. Un fichier absent, illisible ou incomplet ne bloque pas l'écran : les
 * valeurs par défaut de l'application s'appliquent, et un thème inconnu est ignoré.
 */
export async function lireConfiguration(defauts: ConfigurationDemo): Promise<ConfigurationDemo> {
  let lue: Record<string, unknown> = {};
  try {
    const reponse = await fetch('/config.json', { cache: 'no-store' });
    if (reponse.ok) {
      lue = (await reponse.json()) as Record<string, unknown>;
    }
  } catch {
    // Valeurs par défaut.
  }
  const texte = (valeur: unknown, defaut: string) =>
    typeof valeur === 'string' && valeur.trim() ? valeur.trim() : defaut;
  const theme = THEMES.find((connu) => connu === lue['theme']) ?? defauts.theme;
  return {
    nom: texte(lue['nom'], defauts.nom),
    organisme: texte(lue['organisme'], defauts.organisme),
    theme,
  };
}
