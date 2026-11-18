# Interfaces web

Deux espaces de travail Angular 21.2 (Node 24), sans zone, composants autonomes, signaux.

| Espace              | Applications                                     | Bibliothèques                          |
| ------------------- | ------------------------------------------------ | -------------------------------------- |
| `interfaces/socle/` | `espace-citoyen`, `console`, `vitrine`           | `@sira/ui`, `@sira/coquille` (sources) |
| `interfaces/demo/`  | `portail-service`, `registre-admin`, `telephone` | celles du socle, plus `@demo/commun`   |

- `@sira/ui` : la couche CSS (`projects/ui/styles`, variables `--sira-*`) et les composants. Ni appel
  HTTP, ni état partagé, ni règle métier.
- `@sira/coquille` : accès à `/api`, session, gardes, titres de page, pages d'erreur.
- `vitrine` : le catalogue de tous les composants et un écran d'exemple complet (liste, détail,
  formulaire) à recopier.

## Commandes

```sh
cd interfaces/socle            # ou interfaces/demo
npm ci
npm run demarrer -- vitrine   # 4200 ; espace-citoyen 4201, console 4202
npm run lint                  # ESLint, Prettier, et sira.css à jour
npm run tester                # tests unitaires de ui et coquille (socle seulement)
npm run construire            # build de production des trois applications
npm run css                   # socle : réassemble projects/ui/styles/sira.css après une retouche CSS
```

L'espace de démonstration travaille sur une copie non versionnée des bibliothèques
(`interfaces/demo/bibliotheques/`), refaite avant chaque démarrage et chaque build par
`npm run bibliotheques`. Une bibliothèque se modifie toujours dans `interfaces/socle/projects/`.
Ports de la démonstration : 4211, 4212, 4213.

## Image et exécution

Une seule recette pour toutes les interfaces, contexte de build à la racine du dépôt :

```sh
docker build -f deploiement/docker/web.Dockerfile \
  --build-arg ESPACE=interfaces/demo --build-arg APPLICATION=portail-service -t sira/web-portail .
```

Le conteneur (nginx sans privilège, port 8080, lecture seule possible avec `/tmp` en mémoire) se règle
par variables d'environnement :

| Variable    | Rôle                                                                                                                          |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `API_AMONT` | Serveur de l'interface, par exemple `http://service-concours:8080`. `/api/` y est relayé tel quel. Vide : `/api/` répond 404. |
| `RESOLVEUR` | DNS qui résout l'amont. Par défaut `127.0.0.11`, celui de Docker.                                                             |
| `CONFIG_*`  | Chaque variable devient une clé de `/config.json` : `CONFIG_NOM` → `"nom"`.                                                   |

Les applications de démonstration lisent `nom`, `organisme` et `theme` dans `/config.json` au
démarrage : la même image sert le portail des concours et celui des bourses. Thèmes : `socle`,
`console`, `concours`, `bourses`, `entreprises`, `etat-civil`, `nationalite`, `casier`, `diplomes`.

`/sante` répond 200. La politique de contenu n'autorise que la même origine : aucun script ni style en
ligne. C'est pourquoi les composants n'ont pas de styles propres (ESLint le refuse) et qu'on n'écrit
jamais d'attribut `style` : toute la mise en forme vit dans `projects/ui/styles`.

## Ce que le serveur d'une interface doit offrir

| Appel                                         | Réponse attendue                                                                   |
| --------------------------------------------- | ---------------------------------------------------------------------------------- |
| `GET /api/session`                            | 200 `{ "nom": "…", "roles": ["…"] }`, ou 401. Pose le cookie lisible `XSRF-TOKEN`. |
| `GET /api/auth/connexion/sira?retour=/chemin` | Navigation entière : ouvre la session puis redirige vers `retour` (chemin local).  |
| `POST /api/auth/deconnexion`                  | Ferme la session ; peut répondre `{ "redirection": "…" }`.                         |

- Toute écriture porte l'en-tête `X-XSRF-TOKEN`, recopié du cookie.
- Erreurs au format RFC 9457 : `detail` est affiché tel quel, `erreurs: [{ champ, message }]` est
  rattaché aux champs du formulaire.
- Listes paginées : `{ elements, page, taille, total }`, `page` à partir de 1.
- Les modèles TypeScript reprennent les noms du JSON (`cree_le`), sans conversion.
- Rôles de la console : `instructeur`, `autorite`, `auditeur`.

## Écrire un écran

### Ajouter une route

Un dossier par fonction, chargé à la demande, dans `app.routes.ts` :

```ts
{
  path: 'mes-acces',
  title: 'Mes accès',
  canActivate: [gardeConnecte], // ou gardeRole('instructeur')
  loadComponent: () => import('./mes-acces/mes-acces').then((m) => m.MesAcces),
},
```

Le titre devient « Mes accès — Nom de l'application » et le focus va au `h1` de l'écran. Chaque écran
a donc un `h1`, et un seul. Les routes d'erreur (`ROUTES_ERREUR`) restent en dernier.

### Appeler l'API

Un service par ressource ; les écrans n'écrivent jamais de chemin.

```ts
@Injectable({ providedIn: "root" })
export class Demarches {
  private readonly api = inject(Api);

  lister(page: number): Observable<Page<Demarche>> {
    return this.api.lire("/api/demarches", { page, taille: 20 });
  }
  creer(demarche: NouvelleDemarche): Observable<Demarche> {
    return this.api.creer("/api/demarches", demarche);
  }
}
```

Dans l'écran, `rxResource` porte les trois états, et `lireProbleme` traduit l'erreur :

```ts
protected readonly resultat = rxResource({
  params: () => ({ page: this.page() }),
  stream: ({ params }) => this.demarches.lister(params.page),
});
protected readonly probleme = computed(() =>
  this.resultat.error() ? lireProbleme(this.resultat.error()).message : null,
);
```

Un 401 renvoie de lui-même à la connexion puis ramène l'usager à l'écran qu'il quittait. Modèle
complet : `interfaces/socle/projects/vitrine/src/app/exemple/`.

## Composants

Tous s'importent depuis `@sira/ui`. Le catalogue de la vitrine les montre en situation.

```html
<!-- Icone : décorative, toujours à côté d'un texte -->
<sira-icone nom="search" />

<!-- Bouton : primaire, secondaire, danger, discret ; enCours bloque le double envoi -->
<button siraBouton type="submit" [enCours]="envoi()">Enregistrer</button>
<a siraBouton variante="secondaire" routerLink="/exemple">Annuler</a>

<!-- Lien -->
<a siraLien icone="arrow-left" routerLink="..">Retour à la liste</a>
<a siraLien externe href="https://eupl.eu">Lire la licence</a>

<!-- Champ et Saisie : libellé, aide et erreur reliés au champ -->
<sira-champ
  libelle="Numéro d’identification"
  aide="12 chiffres"
  identifiant="nip"
  [erreur]="erreurs().nip"
>
  <input siraSaisie formControlName="nip" inputmode="numeric" />
</sira-champ>

<!-- GroupeFormulaire : boutons radio ou cases à cocher -->
<sira-groupe-formulaire legende="Durée de l’accès" [erreur]="erreurs().duree">
  <label class="sira-choix"
    ><input type="radio" formControlName="duree" value="30" /> 30 jours</label
  >
</sira-groupe-formulaire>

<!-- ResumeErreurs : en haut du formulaire, chaque ligne mène au champ -->
<sira-resume-erreurs
  [erreurs]="[{ champ: 'nip', message: 'Saisissez les 12 chiffres.' }]"
/>

<!-- Alerte : info, succes, attention, erreur -->
<sira-alerte type="erreur" titre="L’état civil ne répond pas pour le moment">
  Rien n’a été transmis. Réessayez dans quelques minutes.
</sira-alerte>

<!-- Bandeau : message de toute l'application -->
<sira-bandeau>Démonstration — données fictives</sira-bandeau>

<!-- Carte -->
<sira-carte titre="Agence des concours" [niveauTitre]="3">
  <sira-etiquette carte-etat etat="succes">Accès en cours</sira-etiquette>
  <p>Nationalité : réponse oui ou non.</p>
  <a carte-actions siraLien routerLink="acces/12">Voir le détail</a>
</sira-carte>

<!-- Etiquette : neutre, info, succes, attention, erreur -->
<sira-etiquette etat="attention">En attente</sira-etiquette>

<!-- Tableau : sous 640 px chaque ligne devient une carte -->
<sira-tableau
  legende="Accès donnés"
  [colonnes]="[{ cle: 'service', libelle: 'Service' }, { cle: 'etat', libelle: 'État' }]"
  [lignes]="acces()"
>
  <ng-template siraCellule="etat" let-ligne>
    <sira-etiquette etat="succes">{{ ligne.etat }}</sira-etiquette>
  </ng-template>
</sira-tableau>

<!-- Pagination -->
<sira-pagination
  [page]="page()"
  [pages]="pages()"
  (changement)="page.set($event)"
/>

<!-- Etapes -->
<sira-etapes
  [etapes]="['Identité', 'Vos pièces', 'Vérification et envoi']"
  [courante]="2"
/>

<!-- Dialogue : confirmation d'un geste qui engage -->
<sira-dialogue
  titre="Retirer l’accès de l’Agence des concours ?"
  [(ouvert)]="ouvert"
>
  <p>Le service ne pourra plus consulter votre nationalité.</p>
  <button
    dialogue-actions
    siraBouton
    variante="danger"
    type="button"
    (click)="retirer()"
  >
    Retirer l’accès
  </button>
</sira-dialogue>

<!-- EtatVide -->
<sira-etat-vide
  titre="Aucun service ne consulte vos informations"
  icone="inbox"
>
  <p>Quand vous autoriserez un service, il apparaîtra ici.</p>
</sira-etat-vide>

<!-- Squelette : pendant un chargement -->
<sira-squelette [lignes]="5" libelle="Chargement des démarches" />

<!-- Entete et Pied : posés une fois, dans app.ts -->
<sira-entete
  service="Espace citoyen"
  [liens]="liens"
  [utilisateur]="session.utilisateur()?.nom"
  (deconnexion)="session.seDeconnecter()"
/>
<sira-pied
  >Sira, socle libre d’interopérabilité entre administrations.</sira-pied
>

<!-- Fil d'Ariane -->
<sira-fil
  [elements]="[{ libelle: 'Mes accès', chemin: '/mon-espace' }, { libelle: 'Agence des concours' }]"
/>

<!-- Onglets -->
<sira-onglets
  libelle="Vos accès"
  [onglets]="[{ id: 'cours', libelle: 'En cours' }, { id: 'finis', libelle: 'Terminés' }]"
  [(actif)]="onglet"
>
  <ng-template siraPanneau="cours"
    >Deux services consultent vos informations.</ng-template
  >
  <ng-template siraPanneau="finis">Aucun accès terminé.</ng-template>
</sira-onglets>

<!-- Synthese : relecture avant envoi -->
<dl siraSynthese>
  <div siraSyntheseLigne cle="Nom">
    OUÉDRAOGO <a synthese-action siraLien routerLink="identite">Modifier</a>
  </div>
</dl>

<!-- Code à comparer et Minuteur -->
<sira-code code="492113" />
<sira-minuteur
  [echeance]="demande().expireLe"
  objet="Cette demande"
  (expire)="recharger()"
/>

<!-- DemandeAcces : le même composant à l'accord, au détail d'un accès et à l'historique -->
<sira-demande-acces
  [demande]="demande()"
  avecDecision
  [enCours]="envoi()"
  (autoriser)="autoriser()"
  (refuser)="refuser()"
/>

<!-- Dates : toujours en toutes lettres -->
{{ acces.cree_le | siraDate: 'jour' }}
```

Les noms exacts des entrées et les types (`ColonneTableau`, `DemandeAcces`, `LienNavigation`…) sont
dans `interfaces/socle/projects/ui/src/lib/` ; le fichier `catalogue.html` de la vitrine montre chaque
composant avec toutes ses options.

## Couleurs

Les couleurs de l'État, sobres : beaucoup de blanc et de gris neutres, une seule couleur d'action.
Les valeurs vivent dans `projects/ui/styles/jetons.css` (primitives et jetons sémantiques) et
`themes.css` (un bloc par `data-theme`) ; les composants ne lisent que les jetons sémantiques.

| Rôle                             | Jeton                                   | Socle                 | Contraste                                                      |
| -------------------------------- | --------------------------------------- | --------------------- | -------------------------------------------------------------- |
| Action, liens                    | `--sira-action`, `--sira-lien`          | `#245c47`             | 7,8:1 sur blanc, blanc dessus 7,8:1                            |
| Titres h1 à h4, nom de l'en-tête | `--sira-texte-fort`                     | `#1f365c`             | 12,1:1                                                         |
| Texte                            | `--sira-texte`                          | `#1b1f23`             | 16,6:1                                                         |
| Texte secondaire                 | `--sira-texte-secondaire`               | `#4a5158`             | 8,0:1                                                          |
| Contour de champ                 | `--sira-bordure-champ`                  | `#6b737b`             | 4,8:1                                                          |
| Erreur, action destructrice      | `--sira-erreur`                         | `#b02a2a`             | 6,5:1                                                          |
| Focus (deux anneaux)             | `--sira-focus-interieur` / `-exterieur` | `#f2b33d` / `#1b1f23` | or ≥ 4,1:1 sur chaque couleur d'action, encre 16,6:1 sur blanc |

- Vert profond pour agir ; bleu nuit pour la hiérarchie ; or en touche (focus, signe) ; rouge réservé aux erreurs et au retrait, jamais décoratif.
- Pas de dégradé, pas d'ombre marquée, pas d'aplat vif.
- Thèmes : `console` en bleu nuit ; chaque administration simulée a sa teinte (`concours`, `bourses`,
  `entreprises`, `etat-civil`, `nationalite`, `casier`, `diplomes`), toutes sombres et peu saturées.
  Une nouvelle couleur de thème doit garder le texte blanc dessus à 4,5:1 au moins (les actuelles
  sont à 7,6:1 au moins) et la couleur sur sa teinte à 4,5:1 au moins.
- L'écran de connexion (`identite/theme/sira/login/resources/css/sira.css`) reprend ces valeurs à la
  main : les changer aux deux endroits.
- Après une modification des styles : `npm run css` (régénère `sira.css`), puis `npm run lint`.

## Écrire les textes

L'usager lit sur un téléphone, parfois avec peu d'habitude de l'écrit administratif.

- Phrases courtes, voix active, vouvoiement. Un écran, une chose à faire.
- Les mots de l'usager, pas ceux du système : « vos informations », « le service », « donner votre
  accord », « retirer l'accès ». Jamais « jeton », « consentement », « requête », « API ».
- Le bouton dit ce qu'il fait : « Envoyer mon dossier », pas « Valider » ni « OK ».
- Un message d'erreur dit ce qui s'est passé et quoi faire : « Saisissez les 12 chiffres de votre
  numéro. » Jamais de code technique, jamais de reproche.
- Dire ce qui est transmis, à qui, pourquoi et pour combien de temps, avant de demander l'accord. Dire
  aussi ce qui ne l'est pas : « Le service ne reçoit pas l'information elle-même. »
- Dates en toutes lettres (« 16 décembre 2026 »), durées en mots (« dans 4 minutes »).
- Typographie française : apostrophe courbe (’), guillemets « », espace avant `: ; ? !`.
- La couleur ne porte jamais seule une information : une étiquette a toujours un texte.
