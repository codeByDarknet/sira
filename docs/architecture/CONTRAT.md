# Contrat d'architecture de Sira

Ce document fixe les règles que tout composant du dépôt respecte : vocabulaire, identités,
cryptographie, formats, journal. Il est normatif. Quand le code et le contrat divergent, on
modifie d'abord le contrat, puis le code. Les routes détaillées sont dans les spécifications
OpenAPI de chaque module (voir [API.md](API.md)).

## 1. Vocabulaire

| Terme | Sens |
|---|---|
| **Membre** | Une administration raccordée. Elle est `service` (elle demande), `registre` (elle fournit) ou les deux. |
| **Registre** | Le système existant d'une administration (état civil, casier…). Sira ne le modifie pas. |
| **Connecteur** | Le composant du socle posé devant un registre. Seul point d'entrée vers ce registre. |
| **Jeu de données** | Ce qu'un registre accepte de fournir, au niveau `STATUT` (oui/non), `CHAMPS` (quelques champs) ou `DOCUMENT` (un PDF). |
| **Démarche** | Un service rendu au citoyen (inscription à un concours). Elle porte une finalité. |
| **Habilitation** | La décision, instruite et approuvée, qui autorise un service à obtenir une liste de jeux pour une démarche. |
| **Demande** | Une requête d'un service, pour un citoyen et une démarche. |
| **Accord** | La décision du citoyen sur une demande. Un accord accepté crée une **autorisation**, bornée dans le temps et retirable. |
| **Attestation** | La réponse d'un registre pour un jeu : signée par le registre, chiffrée pour le service. |
| **Journal** | La trace, en ajout seul et chaînée, de tout ce qui précède. |

## 2. Composants et frontière production / démonstration

Un dossier par module. Dans chacun, le README dit ce qui relève du socle réutilisable et ce qui
est SIMULATION.

```
registres/                 SIMULATION : registre générique (serveur/), données fictives, politiques
passerelle/                routage, référentiel, transport direct mTLS (serveur/), kit client (kit/)
consentement-minimisation/ accords, codes, pseudonymes, vues minimales (consentement/), connecteur
journal/                   journal chaîné, points de contrôle signés, signalements
espace-usager/             serveur de l'espace citoyen (session, relais vers la passerelle)
guichet-agent/             SIMULATION : services de démonstration (concours, bourses, entreprises)
resilience/                disjoncteur, cloison, nouveaux essais, file d'attente durable
commun/                    bibliothèque Java partagée (identité mTLS, JOSE, erreurs, pagination)
console/                   serveur de la console d'administration du socle
identite/                  royaumes Keycloak, extension « code à usage unique », thème ; demo/ : comptes fictifs
pki/                       autorité de certification interne (scripts openssl)
interfaces/                espaces Angular : socle/ (ui, coquille, espace-citoyen, console, vitrine),
                           demo/ (portail-service, registre-admin, telephone)
deploiement/  scripts/  tests/  docs/
```

Le journal, le consentement et la résilience sont des bibliothèques assemblées dans le processus de
la passerelle : une même transaction couvre l'accord, l'échange et sa ligne de journal. La passerelle
dépend de ces modules, jamais l'inverse ; ils ne se parlent qu'au travers d'interfaces étroites
(`CitoyensJournal`, `Echanges`, `Reprise`). Rien du socle ne dépend d'un module de simulation : les
services et registres simulés consomment `passerelle/kit` et `interfaces/socle/projects/ui`.

## 3. Réseaux et points d'entrée

Un seul port est publié : celui du proxy d'entrée (`SIRA_PORT`, 8600 par défaut).

| Adresse | Composant |
|---|---|
| `http://identite.sira.localhost:8600` | Keycloak |
| `http://citoyen.sira.localhost:8600` | Espace citoyen |
| `http://console.sira.localhost:8600` | Console du socle |
| `http://concours.sira.localhost:8600`, `bourses…`, `entreprises…` | Services simulés |
| `http://etat-civil.sira.localhost:8600`, `nationalite…`, `casier…`, `diplomes…` | Back-office des registres simulés |
| `http://telephone.sira.localhost:8600` | Téléphone simulé |

Trois règles de réseau s'imposent ; la liste complète des réseaux Docker est dans
[infrastructure.md](../developpement/infrastructure.md#réseaux).

- Sur le réseau `sira` (passerelle, connecteurs, serveurs des services et du socle, relais de
  Keycloak en HTTPS 8443), le **mTLS est obligatoire**.
- Chaque administration a son réseau privé `adm-<code>`, où vivent le registre et son connecteur.
  Le registre n'est joignable par personne d'autre.
- Chaque interface web est servie par un nginx qui relaie `/api/` vers **son** serveur et rien d'autre.

## 4. Identité : trois populations qui ne se croisent jamais

| Population | Royaume Keycloak | Applications | Fourni par |
|---|---|---|---|
| Citoyens | `sira-citoyens` | espace citoyen uniquement | socle |
| Administrateurs du socle | `sira-socle` | console uniquement | socle |
| Agents des administrations | `demo-agents` | back-office des services et des registres simulés | démo (en réalité : l'annuaire de chaque administration) |

Règles :

1. Un serveur n'accepte qu'**un seul** émetteur de jetons, écrit en dur dans sa configuration.
2. La passerelle accepte deux émetteurs, chacun sur son préfixe de routes : `sira-citoyens` sur `/v1/citoyen/**`, `sira-socle` sur `/v1/admin/**`. Un jeton d'un royaume présenté sur les routes de l'autre est refusé (401).
3. Les navigateurs ne reçoivent **jamais** de jeton. Chaque serveur d'application tient une session (cookie `HttpOnly`, `SameSite=Lax`, propre à son nom d'hôte) et garde les jetons côté serveur. Protection CSRF active.
4. Les serveurs d'application sont des clients OIDC confidentiels authentifiés par **certificat** (`tls_client_auth`, RFC 8705). Aucun secret partagé. Les jetons sont liés au certificat (`cnf.x5t#S256`).
5. Connexion du citoyen : identifiant unique, puis code à usage unique (extension Keycloak) ou clé d'accès. Pas de mot de passe.
6. Rôles du royaume `sira-socle` : `instructeur` (prépare), `autorite` (approuve), `auditeur` (lit). Celui qui instruit une habilitation ne peut pas l'approuver.
7. Les plateformes de service n'authentifient pas le citoyen elles-mêmes : elles créent une demande, et c'est le socle qui identifie le citoyen et recueille son accord (§6).

## 5. Sécurité des échanges entre machines

1. **mTLS partout sur le réseau `sira`** : TLS 1.3, certificat client exigé, autorité interne ECDSA P-256.
2. **Identité d'un membre** = l'URI du champ SAN de son certificat :
   `urn:sira:socle:passerelle`, `urn:sira:socle:espace-citoyen`, `urn:sira:socle:console`,
   `urn:sira:service:<code>`, `urn:sira:registre:<code>` ; Keycloak porte `urn:sira:socle:identite`.
   Chaque route vérifie l'identité attendue. Le nom n'est jamais lu dans un en-tête ou un corps.
   Dans l'autre sens, chaque client sortant nomme le membre qu'il joint et refuse, pendant la poignée de main, un serveur qui n'en porte pas l'identité : la passerelle n'envoie une requête qu'à `urn:sira:registre:<code>` du registre visé, le kit et les serveurs d'application ne parlent qu'à `urn:sira:socle:passerelle`, et les clés et le point de jetons ne sont lus que chez `urn:sira:socle:identite`.
3. **Trois paires de clés par membre**, émises par l'autorité : `tls` (transport), `cachet` (signature), `chiffrement` (réception). Jamais la même clé pour deux usages : un certificat de cachet ou de chiffrement doit porter le `keyUsage` de son usage (un certificat sans `keyUsage` ne vaut pour rien).
4. **Requête passerelle → connecteur** : un JWS (ES256) signé par le cachet de la passerelle, valable 60 secondes, à usage unique (`jti`).
5. **Attestation** : JWS (ES256) signé par le cachet du registre, puis JWE (`ECDH-ES+A256KW`, `A256GCM`) pour le certificat de chiffrement du service. La passerelle transporte et ne peut pas lire.
6. Le connecteur ne chiffre que pour un certificat de chiffrement dont la chaîne remonte à l'autorité Sira **et** dont le SAN est le service nommé dans la requête signée. Ce certificat est validé **avant** toute consultation du registre.
   Un objet signé (requête ou attestation) n'a qu'une seule audience, `aud`, égale à l'identité du destinataire.
7. **Aucun algorithme maison.** Bibliothèques : JSSE, Spring Security, Nimbus JOSE+JWT, `java.security`, `javax.crypto`. Toute comparaison de secret est à temps constant (`MessageDigest.isEqual`).
8. **Aucun secret dans le dépôt.** Clés, mots de passe de base et clé de pseudonymisation sont générés au premier démarrage, dans des volumes, et lus comme fichiers.
9. Le citoyen est désigné chez un service par un **pseudonyme propre à ce service** : HMAC-SHA-256(clé de la passerelle, secteur ‖ identifiant unique), avec longueurs préfixées. L'identifiant unique ne sort de la passerelle que vers les connecteurs.

### Règles de code (toutes obligatoires)

- Toute entrée est validée (Bean Validation), toute liste est paginée et bornée, toute taille de corps est bornée.
- Contrôle d'accès au niveau de l'objet : on vérifie que la demande, l'autorisation ou la ligne de journal appartient bien à l'appelant. Un objet d'autrui répond 404.
- Erreurs au format `application/problem+json` (RFC 9457), sans trace technique ni donnée personnelle.
- Aucune donnée personnelle dans les journaux applicatifs. Jamais de valeur de registre dans le journal Sira.
- SQL uniquement paramétré. Migrations Flyway. Un rôle de base par composant, droits minimaux.
- Conteneurs non-root, système de fichiers en lecture seule quand c'est possible, `no-new-privileges`, aucune capacité.
- En-têtes de sécurité sur toutes les interfaces (CSP stricte sans `unsafe-inline` pour les scripts, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'`).
- Versions exactes épinglées. Aucune dépendance chargée depuis un CDN.
- Chaque règle de sécurité a au moins un test qui prouve le refus.

## 6. Parcours d'une demande

```
service ──(1) crée la demande──▶ passerelle
citoyen ──(2) s'identifie et donne son accord dans l'espace citoyen──▶ passerelle
passerelle ──(3) interroge chaque connecteur──▶ connecteur ──▶ registre
service ──(4) récupère les attestations──▶ passerelle
```

**(2) Trois canaux pour l'accord**, un seul mécanisme :

| Canal | Déroulé |
|---|---|
| `REDIRECTION` | Le service renvoie le navigateur vers `url_accord`. Après la décision, le navigateur revient vers `retour_url?demande=<id>&code_retour=<code>`. |
| `CODE` | Le service affiche un QR code (qui contient `url_accord`) et un code court. Le citoyen scanne ou tape le code dans son espace, sur son téléphone. |
| `NOTIFICATION` | Le service fournit l'identifiant unique (ou le pseudonyme déjà connu). La demande apparaît dans l'espace du citoyen, à sa prochaine connexion. |

Garde-fous :

- `REDIRECTION` : les attestations ne sont remises que si le service présente `code_retour`, que seul le navigateur ayant donné l'accord a reçu. `retour_url` doit appartenir à la liste déclarée du service.
- `CODE` et `NOTIFICATION` par identifiant unique : seul l'écran du service affiche le **code de vérification** à 6 chiffres. Le citoyen le **recopie** sur l'écran d'accord pour accepter : un code faux répond `422 CODE_INVALIDE`, et après 5 codes faux la demande ne peut plus qu'être refusée (`429`). Un accord ne se donne donc pas à distance, sur la foi d'un message. Durée de vie : 5 minutes. Seule une empreinte HMAC des codes est conservée ; un rejeu idempotent de la création ne les rend plus.
- `NOTIFICATION` par identifiant unique ne crée aucune fiche de citoyen et ne révèle pas si la personne est connue : la demande lui est remise à sa connexion.
- `REDIRECTION` et `CODE` acceptent un `identifiant_unique` facultatif : celui que l'usager a saisi lui-même chez le service. La demande est alors réservée à cette personne : pour tout autre citoyen connecté, elle n'existe pas (`404 DEMANDE_INTROUVABLE` par le lien, `404 CODE_INVALIDE` par le code court, compté comme un code faux). Comme pour `NOTIFICATION`, aucune fiche n'est créée et la réponse au service est la même que la personne soit connue ou non ; le reste du canal ne change pas (pas de message, pas d'acceptation d'office, codes inchangés).
- L'écran d'accord est servi par le socle, à ses couleurs. Il dit qui demande, quoi (et sous quelle forme), pourquoi, pour combien de temps. Le refus est aussi visible que l'acceptation.
- Un refus, une expiration et un échec sont journalisés et visibles du citoyen.

**Autorisation.** L'accord accepté crée une autorisation valable pour la durée fixée par la démarche (`0` = usage unique). Tant qu'elle est active, une nouvelle demande du même service pour la même démarche, qui désigne le citoyen par son **pseudonyme**, est acceptée sans nouvel écran, journalisée et visible. Le pseudonyme n'est remis qu'après un accord : c'est le cas d'un agent authentifié du service qui redemande une vérification. Une demande qui désigne la personne par son identifiant unique (tapé sur un poste anonyme) n'est jamais acceptée d'office. Le service n'ouvre le dossier que dans le navigateur dont la demande a reçu l'accord. Le citoyen peut la retirer à tout moment : effet immédiat, attestations en dépôt purgées.

**(3) Tenue aux pannes.** Pour chaque jeu : essai immédiat borné (délai 3 s, 2 tentatives, disjoncteur par registre). En cas d'échec l'élément reste `A_TRAITER` dans une file durable PostgreSQL (`FOR UPDATE SKIP LOCKED`, backoff exponentiel avec gigue) et la demande passe `PARTIELLE`. Les éléments obtenus sont remis sans attendre. Une attestation encore valide, déjà obtenue pour le même service et le même citoyen, est réutilisée (`REUTILISE`) et journalisée.

États d'une demande : `EN_ATTENTE_ACCORD` → `REFUSEE` | `EXPIREE` | `ANNULEE` | `EN_COURS` → `PARTIELLE` → `COMPLETE`.
États d'un élément : `A_TRAITER`, `OBTENU`, `REUTILISE`, `REFUSE` (le registre refuse), `INCONNU` (personne absente du registre), `EXPIRE`.

## 7. API de la passerelle (`https://passerelle:8443`, mTLS)

Tous les corps sont en JSON, en `snake_case`. Dates en ISO 8601 UTC. Identifiants : UUID.

### 7.1 Routes des services — identité exigée : `urn:sira:service:*`

`POST /v1/demandes` — en-tête `Idempotency-Key` obligatoire.

```json
{
  "demarche": "concours-direct",
  "canal": "REDIRECTION",
  "retour_url": "http://concours.sira.localhost:8600/retour",
  "identifiant_unique": null,
  "pseudonyme": null,
  "certificat_chiffrement": "-----BEGIN CERTIFICATE-----…"
}
```

Réponse `201` :

```json
{
  "id": "…", "etat": "EN_ATTENTE_ACCORD",
  "url_accord": "http://citoyen.sira.localhost:8600/accord/…",
  "code_court": "KLPM-4821", "code_verification": "492113",
  "expire_le": "…",
  "jeux": [{"code": "etat-civil.majeur", "libelle": "Vous êtes majeur", "niveau": "STATUT", "registre": "etat-civil"}]
}
```

`identifiant_unique` : obligatoire sur `NOTIFICATION` (sauf `pseudonyme`), facultatif sur `REDIRECTION` et `CODE` (voir les garde-fous ci-dessus). `pseudonyme` : `NOTIFICATION` seulement.

Les jeux demandés sont ceux de l'habilitation approuvée pour (service, démarche). Un service ne choisit pas ses jeux à la volée. Sans habilitation : `403`.

`code_court` (canal CODE) et `code_verification` (canaux CODE et NOTIFICATION par identifiant unique) ne sont rendus qu'à la création ; ils valent `null` sur un rejeu de la même `Idempotency-Key` et sur une acceptation d'office.

`GET /v1/demandes/{id}[?code_retour=…]`

```json
{
  "id": "…", "etat": "PARTIELLE", "pseudonyme": "…",
  "elements": [
    {"jeu": "etat-civil.majeur", "etat": "OBTENU", "attestation": "<JWE compact>"},
    {"jeu": "casier.neant", "etat": "A_TRAITER", "prochaine_tentative_le": "…"}
  ]
}
```

`DELETE /v1/demandes/{id}` annule. `GET /v1/demarches/{code}` décrit la démarche et ses jeux.

### 7.2 Routes du citoyen — identité `urn:sira:socle:espace-citoyen` + jeton `sira-citoyens`

| Route | Rôle |
|---|---|
| `GET /v1/citoyen/demandes?etat=EN_ATTENTE_ACCORD` | Demandes qui attendent sa décision |
| `POST /v1/citoyen/demandes/rattachement` `{ "code_court": "…" }` | Rattache à lui une demande `CODE` scannée ou tapée |
| `GET /v1/citoyen/demandes/{id}` | Détail pour l'écran d'accord |
| `POST /v1/citoyen/demandes/{id}/decision` `{ "decision": "ACCEPTER" \| "REFUSER", "code_verification": "…" }` | Le code recopié est exigé pour accepter quand `code_verification_exige`. Réponse : `{ "retour_url": "…" }` si `REDIRECTION` |
| `GET /v1/citoyen/autorisations` | Services ayant un accès actif, et les informations concernées |
| `DELETE /v1/citoyen/autorisations/{id}` | Retrait de l'accès |
| `GET /v1/citoyen/journal?page=&taille=` | Qui a consulté quoi, quand, pourquoi, avec quel résultat |
| `POST /v1/citoyen/journal/{id}/signalement` `{ "motif": "…" }` | Signaler un accès |

L'identifiant unique du citoyen vient de la revendication `iue` du jeton, jamais d'un paramètre.

### 7.3 Routes d'administration — identité `urn:sira:socle:console` + jeton `sira-socle`

`/v1/admin/membres`, `/registres`, `/jeux`, `/demarches`, `/habilitations` (création, lecture, modification, désactivation), `POST /v1/admin/habilitations/{id}/approbation`, `PUT /v1/admin/membres/{code}/retours` et `PUT /v1/admin/registres/{code}/adresse` (autorité seule), `GET /v1/admin/journal`, `GET /v1/admin/journal/verification`, `GET /v1/admin/signalements`, `GET /v1/admin/etat` (disjoncteurs, file d'attente).

Un jeu se définit sans code :

```json
{
  "code": "etat-civil.majeur", "registre": "etat-civil", "niveau": "STATUT",
  "libelle": "Vous êtes majeur", "validite": "P90D",
  "regle": {"champ": "date_naissance", "operateur": "AGE_AU_MOINS", "valeur": 18}
}
```

Opérateurs : `EGAL`, `AU_MOINS`, `AU_PLUS`, `AGE_AU_MOINS`, `AGE_AU_PLUS`, `EXISTE`, `AUCUN`, `ENCORE_VALIDE`.
Niveau `CHAMPS` : `"champs": ["nom", "prenoms"]`. Niveau `DOCUMENT` : `"document": "extrait-naissance"`.

## 8. API du connecteur (`https://connecteur-<registre>:8443`, mTLS, identité exigée : `urn:sira:socle:passerelle`)

`POST /v1/requetes`, corps `application/jose` : JWS compact signé par la passerelle.

```json
{
  "iss": "urn:sira:socle:passerelle", "aud": "urn:sira:registre:etat-civil",
  "jti": "<identifiant d'échange>", "iat": 0, "exp": 0,
  "sujet": {"identifiant_unique": "…", "pseudonyme": "…"},
  "demandeur": {"membre": "urn:sira:service:concours", "certificat_chiffrement": "<PEM>"},
  "demarche": "concours-direct", "finalite": "…",
  "autorisation": {"id": "…", "accordee_le": "…", "canal": "CODE"},
  "jeu": {"code": "etat-civil.majeur", "niveau": "STATUT",
          "regle": {"champ": "date_naissance", "operateur": "AGE_AU_MOINS", "valeur": 18}}
}
```

Réponse `200` : `{ "attestation": "<JWE compact>" }`. Erreurs : `404` personne inconnue, `422` jeu refusé par le registre, `503` registre indisponible.

Le connecteur est maître chez lui : son fichier de configuration liste, champ par champ, le niveau maximal exposable. Une règle qui dépasse ce niveau est refusée (`422`), quelle que soit l'habilitation centrale. Il tient son propre journal.

Contenu signé de l'attestation :

```json
{
  "iss": "urn:sira:registre:etat-civil", "aud": "urn:sira:service:concours",
  "jti": "<identifiant d'échange>", "iat": 0, "exp": 0, "sub": "<pseudonyme>",
  "demarche": "concours-direct", "jeu": "etat-civil.majeur", "niveau": "STATUT",
  "resultat": {"valeur": true}
}
```

`CHAMPS` : `"resultat": {"champs": {"nom": "…"}}`. `DOCUMENT` : `"resultat": {"document": {"nom": "…", "type": "application/pdf", "sha256": "…", "contenu": "<base64>"}}`.

Le service n'accepte une attestation que si elle est liée en entier à ce qu'il attend : `iss` est le registre qui détient le jeu (et signe avec un cachet portant cette identité), `aud` est lui seul, `sub` est le pseudonyme de la demande, `demarche`, `jeu` et `niveau` sont ceux de sa démarche. Le kit fait ces contrôles (`ClientSira.ouvrir(etat, demarche, jeu)`) ; une attestation valide mais déplacée d'un autre jeu, d'un autre citoyen ou d'une autre démarche est refusée.

## 9. API d'un registre existant (simulée dans `registres/serveur`)

HTTP simple, sur le réseau privé de l'administration : `GET /interne/personnes/{identifiant}` (fiche complète) et `GET /interne/personnes/{identifiant}/documents/{type}` (PDF). C'est volontairement naïf : c'est le système ancien que le connecteur protège.

## 10. Journal

Table en ajout seul (déclencheur qui interdit `UPDATE` et `DELETE`, droits retirés). Chaque ligne porte `hachage = SHA-256(hachage_precedent ‖ contenu canonique)`. Un point de contrôle signé (JWS) est produit périodiquement. `GET /v1/admin/journal/verification` recalcule la chaîne.

Une ligne dit : quand, quel type (`DEMANDE`, `ACCORD`, `REFUS`, `CONSULTATION`, `ECHEC`, `REUTILISATION`, `RETRAIT`, `SIGNALEMENT`, `ADMINISTRATION`), quel service, quel registre, quel jeu et quel niveau, quelle démarche et quelle finalité, quel identifiant d'échange, quel résultat. **Jamais la valeur.** Le citoyen y est désigné par un pseudonyme du secteur `journal`.

## 11. Pile technique

Java 21, Spring Boot 4.1.2, Maven (wrapper), PostgreSQL 17, Keycloak 26.8, Nimbus JOSE+JWT 10.10, Resilience4j 2.4.0, Flyway. Angular 21.2 (LTS), TypeScript 5.9, nginx non privilégié. Tests : JUnit, Testcontainers, Playwright.
Paquet Java racine : `bf.sira`. Code métier en français, termes techniques en anglais.

## 12. Transport

Le transport passerelle → connecteur est derrière une interface. Il n'existe qu'une valeur, `DIRECT` (mTLS). Une autre route pourrait s'y brancher ; les signatures et le chiffrement des §5.4 à 5.6 n'en dépendraient pas.
