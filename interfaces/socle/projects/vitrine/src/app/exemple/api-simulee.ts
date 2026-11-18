import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { Observable, delay, of, throwError } from 'rxjs';
import { Demarche, NouvelleDemarche } from './demarches';

const RACINE = '/api/exemple/demarches';

const NOMS: [string, string, string][] = [
  [
    'Inscription au concours de la fonction publique',
    'Agence des concours',
    'Vérifier que le candidat remplit les conditions du concours.',
  ],
  [
    'Demande de bourse d’études',
    'Office des bourses',
    'Vérifier le diplôme et la nationalité de l’étudiant.',
  ],
  [
    'Demande de passeport',
    'Direction de l’identification',
    'Vérifier l’état civil et la nationalité du demandeur.',
  ],
  [
    'Création d’entreprise',
    'Maison de l’entreprise',
    'Vérifier l’identité et le casier judiciaire du dirigeant.',
  ],
  ['Inscription à l’université', 'Université publique', 'Vérifier le diplôme du baccalauréat.'],
  [
    'Demande de permis de conduire',
    'Direction des transports',
    'Vérifier l’état civil du candidat.',
  ],
  [
    'Recrutement d’agents de santé',
    'Ministère de la santé',
    'Vérifier les diplômes et le casier judiciaire.',
  ],
];

const demarches: Demarche[] = NOMS.map(([nom, organisme, finalite], rang) => ({
  id: `d-${rang + 1}`,
  nom,
  organisme,
  finalite,
  duree_jours: rang % 3 === 0 ? 0 : 30 * rang,
  etat: rang === 3 ? 'SUSPENDUE' : 'ACTIVE',
  cree_le: new Date(Date.UTC(2026, 8, 3 + 9 * rang, 9, 30)).toISOString(),
}));

function reponse<T>(corps: T, statut = 200): Observable<HttpResponse<T>> {
  return of(new HttpResponse({ status: statut, body: corps })).pipe(delay(250));
}

function refus(statut: number, detail: string, erreurs?: { champ: string; message: string }[]) {
  return throwError(() => new HttpErrorResponse({ status: statut, error: { detail, erreurs } }));
}

/**
 * Faux serveur de l'écran d'exemple, en mémoire. Il rend les mêmes formes que les vrais
 * serveurs : page `{ elements, page, taille, total }`, erreurs RFC 9457.
 */
export const apiSimulee: HttpInterceptorFn = (requete, suite) => {
  const adresse = new URL(requete.urlWithParams, 'http://local');
  if (!adresse.pathname.startsWith(RACINE)) {
    return suite(requete);
  }
  const id = decodeURIComponent(adresse.pathname.slice(RACINE.length + 1));
  const trouvee = demarches.find((demarche) => demarche.id === id);

  if (requete.method === 'GET' && !id) {
    const recherche = (adresse.searchParams.get('recherche') ?? '').toLocaleLowerCase('fr');
    const taille = Number(adresse.searchParams.get('taille') ?? 5);
    const page = Number(adresse.searchParams.get('page') ?? 1);
    const retenues = demarches.filter((demarche) =>
      `${demarche.nom} ${demarche.organisme}`.toLocaleLowerCase('fr').includes(recherche),
    );
    const elements = retenues.slice((page - 1) * taille, page * taille);
    return reponse({ elements, page, taille, total: retenues.length });
  }
  if (requete.method === 'POST' && !id) {
    const saisie = requete.body as NouvelleDemarche;
    if (demarches.some((demarche) => demarche.nom === saisie.nom)) {
      return refus(422, 'Cette démarche ne peut pas être enregistrée.', [
        { champ: 'nom', message: 'Une démarche porte déjà ce nom. Choisissez un autre nom.' },
      ]);
    }
    const creee: Demarche = {
      ...saisie,
      id: `d-${demarches.length + 1}`,
      etat: 'ACTIVE',
      cree_le: new Date().toISOString(),
    };
    demarches.unshift(creee);
    return reponse(creee, 201);
  }
  if (!trouvee) {
    return refus(404, 'Cette démarche n’existe pas ou n’existe plus.');
  }
  if (requete.method === 'DELETE') {
    demarches.splice(demarches.indexOf(trouvee), 1);
    return reponse(null, 204);
  }
  return reponse(trouvee);
};
