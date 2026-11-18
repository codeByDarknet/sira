import { CHEMIN_ACCORD } from '../commun/identifiant';

/**
 * Lit le contenu d'un QR code. Seule une adresse d'écran d'accord de ce site est admise :
 * même origine, chemin `/accord/<identifiant>`, sans paramètre. Tout autre contenu (autre
 * site, autre page, texte quelconque) est refusé : un QR code ne doit mener nulle part ailleurs.
 *
 * @returns l'identifiant de la demande, ou `null`.
 */
export function lireAdresseAccord(contenu: string, origine: string): string | null {
  const texte = contenu.trim();
  if (texte.length > 200 || !URL.canParse(texte)) {
    return null;
  }
  const adresse = new URL(texte);
  const etrangere =
    adresse.origin !== origine ||
    adresse.username !== '' ||
    adresse.password !== '' ||
    adresse.search !== '' ||
    adresse.hash !== '';
  if (etrangere) {
    return null;
  }
  return CHEMIN_ACCORD.exec(adresse.pathname)?.[1].toLowerCase() ?? null;
}
