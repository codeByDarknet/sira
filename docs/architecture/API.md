# API

Le [contrat](CONTRAT.md) fixe les règles communes (JSON en `snake_case`, dates ISO 8601 UTC,
identifiants UUID, erreurs RFC 9457) et les routes de la passerelle vues des services et des
connecteurs. Cette page complète avec ce que voient les écrans : l'espace citoyen et la console.

## Les spécifications font foi

Chaque module publie un fichier OpenAPI 3.1 tiré de ses contrôleurs. Un test `ContratOpenApiTest`
dans chaque module (sauf les registres simulés) compare les routes Spring au fichier et valide les
réponses réelles contre ses schémas. En cas d'écart avec cette page, c'est le fichier qui a raison.

| Module | Spécification | Accès |
|---|---|---|
| Passerelle | [`passerelle/openapi.yaml`](../../passerelle/openapi.yaml) | mTLS, jeton du royaume `sira-citoyens` ou `sira-socle` |
| Consentement et minimisation | [`consentement-minimisation/openapi.yaml`](../../consentement-minimisation/openapi.yaml) | mTLS (et jeton citoyen) ; requête JWS vers le connecteur |
| Journal | [`journal/openapi.yaml`](../../journal/openapi.yaml) | mTLS et jeton citoyen ou console |
| Résilience | [`resilience/openapi.yaml`](../../resilience/openapi.yaml) | mTLS et jeton console |
| Espace usager | [`espace-usager/openapi.yaml`](../../espace-usager/openapi.yaml) | session, jeton CSRF |
| Guichet agent  | [`guichet-agent/openapi.yaml`](../../guichet-agent/openapi.yaml) | session, OIDC `demo-agents`, jeton CSRF |
| Registres  | [`registres/openapi.yaml`](../../registres/openapi.yaml) | voir le fichier |

**Erreurs.** Une erreur a la forme `{ "type", "title", "status", "detail" }`, avec un `type` en
`urn:sira:erreur:<code>` (par exemple `urn:sira:erreur:code-invalide`). Les erreurs propres au
métier ajoutent un `code` en majuscules (`HABILITATION_ABSENTE`, `CODE_INVALIDE`,
`SEPARATION_DES_ROLES`…) ; une saisie refusée porte `code: VALIDATION` et la liste
`erreurs: [{ "champ", "message" }]`. La liste exacte, route par route, est dans les fichiers
OpenAPI ci-dessus.

**Listes.** `?page=1&taille=20` (taille au plus 100) rend
`{ "elements": [], "page": 1, "taille": 20, "total": 0 }`.

## Session des serveurs d'application

L'espace citoyen, la console et les back-offices simulés partagent les mêmes routes de session.
`GET /api/session` rend `{ "nom", "roles": [] }` (ou `401`) et pose le cookie `XSRF-TOKEN` ;
toute requête qui modifie renvoie sa valeur dans l'en-tête `X-XSRF-TOKEN`. La connexion est une
navigation vers `GET /api/auth/connexion/sira?retour=/chemin` (chemin local uniquement). La
déconnexion, `POST /api/auth/deconnexion`, rend `{ "url" }` : l'adresse où envoyer le navigateur
pour fermer aussi la session chez le fournisseur d'identité.

## Espace citoyen

Le serveur de l'espace citoyen relaie `/api/**` vers `/v1/citoyen/**` de la passerelle, sans
transformer : `GET /api/demandes` devient `GET /v1/citoyen/demandes`. Seules les routes listées
dans `RoutesCitoyen` passent.

**Demande vue par le citoyen**

```json
{
  "id": "…", "etat": "EN_ATTENTE_ACCORD", "canal": "CODE",
  "creee_le": "…", "expire_le": "…", "code_verification_exige": true,
  "service": { "code": "concours", "nom": "Concours directs 2026", "organisme": "Ministère de la Fonction publique" },
  "demarche": { "code": "concours-direct", "libelle": "Inscription à un concours direct", "finalite": "Vérifier que vous remplissez les conditions du concours.", "duree_autorisation_jours": 30 },
  "jeux": [
    { "code": "etat-civil.majeur", "libelle": "Vous êtes majeur", "niveau": "STATUT", "registre": { "code": "etat-civil", "nom": "État civil" }, "detail": "Réponse : oui ou non" }
  ]
}
```

- `GET /demandes?etat=EN_ATTENTE_ACCORD` : liste paginée de demandes.
- `POST /demandes/rattachement` `{ "code_court": "KLPM-4821" }` → la demande. Le QR code contient `url_accord`, soit `/accord/{id}` : dans ce cas l'écran appelle `POST /demandes/{id}/rattachement` sans corps.
- `GET /demandes/{id}` → la demande.
- `POST /demandes/{id}/decision` `{ "decision": "ACCEPTER", "code_verification": "492113" }` → `{ "etat": "EN_COURS", "retour_url": "…" | null }`.
  - Le code n'est jamais renvoyé au citoyen : il le recopie depuis l'écran du service. Il est exigé pour accepter quand `code_verification_exige` est vrai (canaux CODE et NOTIFICATION par identifiant), jamais pour refuser.
  - Code absent ou faux : `422 CODE_INVALIDE`. Après 5 codes faux sur une demande (`sira.passerelle.demandes.essais-verification-max`), `429 TROP_DE_REQUETES` : il ne reste qu'à refuser.
- `POST /demandes/rattachement` : 5 codes courts faux par citoyen et par `duree-accord` (`essais-rattachement-max`), 1 000 pour tous les citoyens ensemble (`essais-rattachement-global-max`), puis `429 TROP_DE_REQUETES`.
- Une demande désignée par `identifiant_unique` (canal NOTIFICATION, ou REDIRECTION et CODE quand le service le transmet) n'apparaît dans la liste qu'une fois la personne connectée, et pour elle seule ; aucune fiche n'est créée pour un identifiant tapé.

**Autorisation**

```json
{
  "id": "…", "etat": "ACTIVE", "accordee_le": "…", "expire_le": "…", "retiree_le": null,
  "service": { "code", "nom", "organisme" }, "demarche": { "code", "libelle", "finalite" },
  "jeux": [{ "code", "libelle", "niveau", "registre": { "code", "nom" } }],
  "consultations": 3, "derniere_consultation_le": "…"
}
```

- `GET /autorisations?etat=ACTIVE|RETIREE|EXPIREE` : liste paginée. `GET /autorisations/{id}`. `DELETE /autorisations/{id}` → `204`.

**Ligne de journal vue par le citoyen**

```json
{
  "id": "…", "horodatage": "…", "type": "CONSULTATION", "resultat": "REUSSI",
  "service": { "code", "nom" }, "registre": { "code", "nom" } ,
  "jeu": { "code", "libelle", "niveau" }, "demarche": { "code", "libelle" }, "finalite": "…",
  "canal": "CODE", "id_echange": "…", "signalee": false
}
```

- `GET /journal?page=&taille=&type=&service=` : liste paginée, la plus récente d'abord. `resultat` : `REUSSI`, `REFUSE`, `ECHEC`, `EN_ATTENTE`.
- `POST /journal/{id}/signalement` `{ "motif": "…" }` (10 à 500 caractères) → `201`.
- `GET /resume` → `{ "demandes_en_attente": 1, "autorisations_actives": 2, "consultations_30_jours": 7 }`.

## Console

Le serveur de la console relaie `/api/**` vers `/v1/admin/**`, selon la liste de `RoutesConsole`.
Les trois rôles lisent ; l'`instructeur` écrit ; l'`autorite` approuve.

| Ressource | Forme |
|---|---|
| `/membres` | `{ "code", "nom", "organisme", "roles": ["SERVICE","REGISTRE"], "identite": "urn:sira:service:concours", "retours_autorises": ["http://…/retour"], "actif": true }` |
| `/registres` | `{ "code", "nom", "membre", "adresse": "https://connecteur-etat-civil:8443", "transport": "DIRECT", "actif": true }` |
| `/jeux` | forme du contrat §7.3, plus `"description"`, `"actif"` |
| `/demarches` | `{ "code", "libelle", "finalite", "base_legale", "service": "concours", "duree_autorisation_jours": 30, "actif": true }` |
| `/habilitations` | `{ "id", "service", "demarche", "jeux": ["etat-civil.majeur"], "etat": "BROUILLON" \| "SOUMISE" \| "APPROUVEE" \| "REJETEE" \| "SUSPENDUE", "justification", "instruite_par", "decidee_par", "decidee_le" }` |

Pour chaque ressource : `GET` (liste paginée), `GET /{code}`, `POST`, `PUT /{code}`, `DELETE /{code}` (désactivation, jamais d'effacement).

Ce que l'instructeur ne change pas seul (`409 CONFLIT` sur `PUT /{code}`) :

- un jeu cité par une habilitation SOUMISE, APPROUVEE ou SUSPENDUE, ou par une autorisation non retirée : seul `actif` change encore ; une autre question se publie sous un nouveau code ;
- les `retours_autorises` d'un membre : `PUT /membres/{code}/retours` `{ "retours_autorises": [] }`, réservé à l'autorité ;
- l'`adresse` d'un registre : `PUT /registres/{code}/adresse` `{ "adresse": "https://…" }`, réservé à l'autorité.

Ces deux décisions sont journalisées (`MODIFICATION_RETOURS`, `MODIFICATION_ADRESSE`).

- `POST /habilitations/{id}/soumission`, `/approbation`, `/rejet` `{ "motif" }`, `/suspension` `{ "motif" }`. L'instructeur ne peut pas décider de sa propre habilitation : `403 SEPARATION_DES_ROLES`.
- `POST /jeux/essai` `{ "jeu": {…}, "identifiant_unique": "…" }` : évalue une règle sur une personne fictive, pour vérifier une question avant de la publier. La réponse ne contient que le niveau demandé.
- `GET /journal?page=&type=&service=&registre=` : lignes sans désignation directe du citoyen (pseudonyme de journal tronqué).
- `GET /journal/verification` → `{ "valide": true, "lignes": 1284, "dernier_hachage": "…", "dernier_point_de_controle": { "horodatage", "ligne", "jws" }, "premiere_rupture": null }`.
- `GET /habilitations?etat=SOUMISE` : le filtre `etat` accepte chaque état d'habilitation ; sans filtre, toutes.
- `GET /signalements?etat=A_TRAITER|TRAITE` (liste paginée, sans filtre : tous), `POST /signalements/{id}/traitement` `{ "reponse" }` (10 à 1000 caractères) → `204`, réservé à l'instructeur et à l'autorité.
- `GET /etat` → `{ "registres": [{ "code", "nom", "disjoncteur": "FERME" | "OUVERT" | "SEMI_OUVERT", "en_file": 3, "dernier_succes_le", "dernier_echec_le" }], "file": { "a_traiter": 3, "plus_ancien_le": "…" } }`.
- `GET /statistiques` → `{ "demandes_jour", "accords_jour", "refus_jour", "consultations_jour", "pieces_evitees_total" }`.

Les API des services et des registres simulés restent internes à chaque plateforme de
démonstration : elles ne sont décrites que dans leurs fichiers OpenAPI.
