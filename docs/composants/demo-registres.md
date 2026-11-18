# Registres simulés et données fictives

Quatre administrations fictives tiennent chacune un registre : état civil, nationalité, casier
judiciaire, diplômes. Elles ne connaissent pas Sira : chacune expose une API interne naïve, que
seul le connecteur posé devant elle appelle. Tout est fictif, et chaque document le dit.

| Élément | Emplacement |
|---|---|
| Serveur (un seul binaire, lancé une fois par registre) | `registres/serveur` |
| Écran des agents | `interfaces/demo/projects/registre-admin` |
| Description et personnes de chaque registre | `registres/donnees/<code>/` |
| Politique locale de chaque connecteur | `registres/connecteurs/<code>.yaml` |
| Amorçage de la passerelle | `passerelle/amorcage-demo/` |

## Un binaire, quatre registres

Ce qui distingue un registre tient dans son dossier de données, monté en lecture seule sur
`/donnees` :

- `registre.yaml` : code, nom, organisme, champs d'une fiche (ordre, type, ce qu'un agent peut
  corriger) et documents délivrés. Un champ est de type `TEXTE`, `DATE`, `NOMBRE`, `BOOLEEN` ou
  `CHOIX` ;
- `personnes.json` : les fiches, importées au démarrage. Une fiche déjà présente n'est pas
  réécrite : les corrections faites pendant la démonstration survivent à un redémarrage.

Ajouter un cinquième registre ne demande aucun code : un dossier de données, une base, une
politique de connecteur.

## API interne (contrat §9)

Sans authentification, sur le réseau privé de l'administration (`adm-<code>`), jamais publiée.

| Route | Réponse |
|---|---|
| `GET /interne/personnes/{id}` | la fiche, objet JSON plat ; `404` si la personne est inconnue |
| `GET /interne/personnes/{id}/documents/{type}` | un PDF marqué « DOCUMENT FICTIF — DÉMONSTRATION », sans emblème ; `404` si la personne ou le type est inconnu |

Un identifiant qui n'a pas douze chiffres ne désigne personne : `404`, sans lecture en base.

## Contrat commun (`registres/openapi.yaml`)

Faute de contrat partagé entre registres, Sira propose le sien, en OpenAPI 3.1. Mêmes conditions
que l'API interne. Les connecteurs de l'état civil, de la nationalité et du casier le lisent au moyen
de leur fichier `registres/raccordements/<code>.yaml`.

| Route | Réponse |
|---|---|
| `GET /v1/etat-civil/personnes/{id}` | identité structurée (naissance, filiation, acte) |
| `GET /v1/nationalite/personnes/{id}` | nationalité et pièce qui l'établit |
| `GET /v1/casier/personnes/{id}` | liste des condamnations, vide si le bulletin n° 3 est néant |
| `GET /v1/sante` | `{ "statut": "OK" }` |

`404` si la personne est inconnue, une erreur `5xx` (problème RFC 9457) si le registre est en panne. `ApiContratTest` vérifie
chaque réponse du serveur contre le contrat.

## Back-office des agents (`/api/**`)

Session OIDC sur le royaume `demo-agents`, client `demo-<code>` authentifié par certificat. Chaque
route `/api/**` exige que l'attribut `administration` de l'agent égale le code du registre : un
agent d'une autre administration reçoit `403`. Les écritures exigent le jeton CSRF.

| Route | Rôle |
|---|---|
| `GET /api/registre` | description du registre : champs et documents |
| `GET /api/personnes?page=&taille=&recherche=` | liste paginée ; recherche par nom, prénoms ou identifiant, sans tenir compte des accents |
| `GET /api/personnes/{id}` | `{ identifiant, fiche, modifiee_le }` |
| `PATCH /api/personnes/{id}` `{ "champs": { … } }` | corrige des champs déclarés `modifiable` ; tout autre champ est refusé (`422`) |
| `GET /api/personnes/{id}/documents/{type}` | le PDF, sans trace dans les appels reçus |
| `GET /api/panne`, `PUT /api/panne` `{ "mode", "delai_ms" }` | panneau de pannes |
| `GET /api/appels?page=&taille=` | appels reçus par l'API interne |

## Panneau de pannes

Le panneau agit sur l'API interne seulement : l'écran des agents répond toujours.

| Mode | Effet sur un appel de Sira | Ce que Sira montre |
|---|---|---|
| `NORMAL` | réponse immédiate | l'information est obtenue |
| `LENT` | réponse après `delai_ms` (0,1 à 30 s) | au-delà du délai du connecteur, l'élément passe en file d'attente |
| `ERREURS` | `500` à chaque appel | le disjoncteur du registre s'ouvre, la demande devient partielle |
| `MUET` | la connexion est acceptée, rien ne revient (`sira.registre.duree-silence`, 30 s) | même issue que `LENT`, sans réponse du tout |

L'état est en base : il survit à un redémarrage. Revenir à `NORMAL` suffit pour que la file de la
passerelle se vide. Chaque appel est noté (heure, fiche ou document, état du registre, statut,
durée, origine), jamais la personne concernée.

## Données fictives

26 personnes à l'état civil, à la nationalité et au casier, 24 aux diplômes. Une même personne a
le même identifiant, le même nom, les mêmes prénoms et la même date de naissance partout (vérifié
par `JeuDeDonneesTest`). Les six comptes citoyens de `identite/demo/sira-citoyens-users-0.json`
existent dans les quatre registres.

| Identifiant | Personne | Ce qu'elle montre |
|---|---|---|
| `990048217350` | KABORÉ Wendkuni Aïcha | tout est conforme : concours et bourse aboutissent |
| `990029915477` | SOMÉ Dieudonné Yirbar | casier avec mentions : « casier vierge » répond non |
| `990063390218` | ZONGO Pingdwendé Rasmata | née en 1986 : « 35 ans au plus » répond non |

Parmi les vingt autres : un mineur, une personne de nationalité malienne, une personne sans
baccalauréat, deux personnes absentes du registre des diplômes.

### Ajouter des citoyens fictifs

```sh
scripts/sira citoyens importer personnes.csv --correspondance colonnes.json --essai   # contrôle seul
scripts/sira citoyens importer personnes.csv --correspondance colonnes.json
```

Le fichier est un tableau JSON (ou `{ "personnes": [ … ] }`) ou un CSV dont la première ligne
nomme les colonnes (séparateur `;` ou `,`, guillemets à la manière d'un tableur). Les noms
attendus sont ceux des champs déclarés dans `registres/donnees/*/registre.yaml` :
`identifiant_unique` (12 chiffres), `nom`, `prenoms`, `date_naissance` (AAAA-MM-JJ),
`lieu_naissance`, `sexe`, `nom_pere`, `nationalite`, `mode_acquisition`, `bulletin3`,
`baccalaureat_obtenu`, etc. `--correspondance` renomme les colonnes d'un fichier venu d'ailleurs :

```json
{ "NIN": "identifiant_unique", "Nom de famille": "nom", "Prénoms": "prenoms", "Né le": "date_naissance" }
```

Chaque personne entre à l'état civil, et dans un autre registre dès qu'un de ses champs propres
est fourni (sans champ du casier, la personne y est inconnue). Les champs obligatoires, les types (date, nombre, oui/non, choix) et les doublons sont
contrôlés avant toute écriture : une seule erreur et rien n'est écrit. Une personne déjà présente
n'est pas modifiée ; relancer l'import ne change rien. Chacune reçoit un compte citoyen dans
`sira-citoyens` (nom d'utilisateur : son identifiant ; messagerie fictive
`<identifiant>@telephone.sira.example`).

## Politiques des connecteurs

`registres/connecteurs/<code>.yaml` se monte sur `/etc/sira/politique.yaml` du connecteur
(`SIRA_CONNECTEUR_POLITIQUE`). Chaque registre n'expose que ce que la démonstration demande :

| Registre | Oui / non | Champs | Documents |
|---|---|---|---|
| état civil | âge d'au moins 18 ans, d'au plus 35 ans | nom, prénoms, date de naissance | `extrait-naissance` |
| nationalité | nationalité burkinabè, rien d'autre | aucun | `certificat-nationalite` |
| casier | bulletin n° 3 néant (liste `condamnations` vide, opérateur `AUCUN`) | aucun | `bulletin-3` |
| diplômes | baccalauréat obtenu | série, année, mention | `attestation-diplome` |

## Amorçage de la passerelle

`passerelle/amorcage-demo/complet.yaml` déclare, sous la forme des ressources d'administration
(`API.md`, contrat §7.3) : les membres `concours`, `bourses` et `entreprises`, les quatre registres,
douze jeux et trois démarches avec leur habilitation approuvée.

| Démarche | Service | Jeux habilités |
|---|---|---|
| `concours-direct` | `concours` | `etat-civil.nom-prenoms`, `etat-civil.majeur`, `etat-civil.age-35-au-plus`, `nationalite.burkinabe`, `casier.neant` |
| `bourse-etudes` | `bourses` | `nationalite.burkinabe`, `etat-civil.identite`, `diplomes.baccalaureat`, `diplomes.attestation` |
| `immatriculation-commercant` | `entreprises` | `etat-civil.identite`, `etat-civil.extrait-naissance`, `nationalite.burkinabe`, `casier.neant` |

`AmorcageTest` vérifie que chaque jeu est admis par la politique du connecteur et connu du
registre.

### Raccorder le registre des diplômes depuis la console

`passerelle/amorcage-demo/sans-diplomes.yaml` est le même amorçage sans le membre, le registre et les
jeux `diplomes` ; la bourse n'y demande que la nationalité et l'identité. Le registre des diplômes
et son connecteur tournent déjà : seule la passerelle les ignore. Depuis la console :

1. **Membre** (instructeur) : code `diplomes`, nom « Diplômes », rôle `REGISTRE`, identité
   `urn:sira:registre:diplomes`.
2. **Registre** : code `diplomes`, membre `diplomes`, adresse `https://connecteur-diplomes:8443`,
   transport `DIRECT`.
3. **Jeux** : `diplomes.baccalaureat` (`STATUT`, règle `baccalaureat_obtenu` `EGAL` `true`) et
   `diplomes.attestation` (`DOCUMENT`, `attestation-diplome`), validité `P365D`. L'essai d'une
   question sur `990048217350` doit répondre oui.
4. **Habilitation** : pour `bourses` et `bourse-etudes`, une nouvelle habilitation qui ajoute ces
   deux jeux aux deux premiers, puis soumission.
5. **Approbation** par un compte `autorite`, différent de l'instructeur.
6. Sur le portail des bourses, une nouvelle demande propose maintenant le baccalauréat et
   l'attestation ; la liste des appels reçus par le registre des diplômes montre les deux appels.

## Assemblage

| | Serveur | Écran |
|---|---|---|
| Conteneur | `registre-<code>` | `web-<code>` |
| Image | `java.Dockerfile`, `MODULE=registres/serveur` | `web.Dockerfile`, `ESPACE=interfaces/demo`, `APPLICATION=registre-admin` |
| Port | 8080 (HTTP), supervision 8081 en local | 8080 |
| Réseaux | `adm-<code>` | `entree`, `adm-<code>` |
| Identité | membre `demo-<code>` (certificat `tls` seul) | sans objet |

Variables du serveur :

| Variable | Valeur |
|---|---|
| `SIRA_REGISTRE_DOSSIER` | `/donnees` par défaut ; y monter `registres/donnees/<code>` en lecture seule |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | base `registre_<code>` de `demo-db` (tiret remplacé par `_`), secret `base.motdepasse` |
| `SIRA_OIDC_EMETTEUR`, `SIRA_OIDC_URL_INTERNE`, `SIRA_OIDC_URL_PUBLIQUE` | royaume `demo-agents` et adresse publique `http://<code>.sira.localhost:<port>` |
| `SIRA_REGISTRE_DUREE_SILENCE` | facultatif, durée du mode `MUET` |

Variables de l'écran : `API_AMONT=http://registre-<code>:8080`, `CONFIG_NOM`, `CONFIG_ORGANISME`,
`CONFIG_THEME=<code>`. Le serveur dépend de `pki`, `demo-db` et `keycloak` ; le connecteur du
registre reçoit `SIRA_CONNECTEUR_REGISTRE_URL=http://registre-<code>:8080`.
