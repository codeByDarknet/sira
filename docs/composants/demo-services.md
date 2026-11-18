# Plateformes de service simulées

Module `guichet-agent/serveur` (serveur) et application `interfaces/demo/projects/portail-service` (écrans).
Une plateforme de service est le site d'une administration qui reçoit une démarche : ici un
concours direct, une bourse d'études et l'immatriculation d'un commerçant. **Un seul binaire et
une seule interface servent les trois** : ce qui les distingue est de la configuration.

Le citoyen n'a pas de compte. Sira l'identifie, lui montre ce qui sera transmis, et les registres
répondent à sa place ; il ne fournit lui-même que ce qu'aucun registre ne détient (un CV, un
téléphone de contact). Le service ne connaît de lui qu'un pseudonyme.

## Parcours du citoyen

Trois étapes : **Votre demande**, **Votre accord dans Sira**, **Votre dossier**.

1. **Accueil** (`/`) : la démarche, ce qui sera vérifié, les pièces évitées.
2. **Votre demande** (`/commencer`) : le formulaire de la démarche. Seul le champ « Votre identifiant
   unique » (12 chiffres) se saisit. Les informations des registres sont affichées vides et
   verrouillées, chacune avec « Rempli par … » : oui/non (`STATUT`) en boutons radio désactivés, un
   champ désactivé par information (`CHAMPS`), une carte de fichier par pièce (`DOCUMENT`).
   « Suivant » ouvre la fenêtre « Ce que nous allons récupérer » (information → registre, et le
   rappel que rien n'est transmis sans accord).
3. **Accord** : « Continuer avec Sira » (canal `REDIRECTION`, le navigateur part vers `url_accord`
   puis revient sur `/retour?demande=…&code_retour=…`). Le lien « Autres façons de donner mon
   accord » mène à `/accord/choix` : code à scanner avec un téléphone (canal `CODE`) ou demande
   reçue sur le téléphone (canal `NOTIFICATION`). **L'identifiant saisi à l'étape 1 sert aux trois
   canaux** : il est transmis à Sira, qui vérifie qu'il désigne la personne qui donne son accord,
   et **n'est jamais enregistré** par le service. Si Sira répond que l'accès était déjà donné (pas
   d'accord attendu), le dossier n'est **pas** ouvert dans ce navigateur. Si l'identifiant saisi
   n'est pas celui de la personne connectée à Sira, l'écran d'accord affiche « Cette demande est
   introuvable », sans dire si l'identifiant existe, et le service ne reçoit rien. La fenêtre se
   ferme par « Annuler ».
4. **Récupération de vos informations** (`/recuperation`) : une ligne par information, avec son
   registre et son état (en cours, reçu, en attente car le registre ne répond pas, non transmis),
   mise à jour seule.
5. **Votre dossier** (`/dossier`) : le formulaire revient prérempli. Ce qui vient d'un registre est
   verrouillé (« Vérifié par … ») ; une pièce reçue se prévisualise (nouvel onglet) ou se
   télécharge. La personne complète les informations du service (`sira.formulaire.declarations`) :
   une saisie avec son bouton « Enregistrer », ou une carte de fichier qui accepte un PDF (un autre
   fichier est refusé à l'écran, « Choisissez un fichier PDF. », comme par le serveur)
   et peut remplacer par un PDF une information que le registre n'a définitivement pas transmise ;
   une information encore attendue reste verrouillée et arrivera seule. « Déposer mon dossier »
   fige les déclarations et affiche le numéro de dossier ; les réponses attendues continuent
   d'arriver.

Deux cas sont traités sans inquiéter le citoyen :

- **Un registre ne répond pas** (demande `PARTIELLE`) : l'écran dit que le dossier est enregistré
  et que la vérification manquante se fera seule. Le serveur relance, l'écran se met à jour.
- **Un registre répond « non »** : le dossier n'est jamais rejeté automatiquement ; il attend
  l'examen d'un agent.

## Espace des agents

`/agent`, derrière la connexion OIDC du socle (royaume `demo-agents`, client `demo-<code>`). L'agent
doit porter l'attribut `administration` égal au code du service, sinon `403`.

- Liste paginée des dossiers, filtrable par état ; détail d'un dossier et de ses demandes. Le
  détail sépare ce qui est « Vérifié par <registre> » de ce qui est « Déclaré par l'usager » ; les
  pièces s'ouvrent en carte (prévisualiser, télécharger).
- Redemander une vérification au citoyen : ouvre une demande `NOTIFICATION` désignée par le
  pseudonyme. L'agent ne consulte jamais un registre lui-même, et ne voit ni le pseudonyme ni
  l'identité du citoyen. Une seule demande en attente par dossier (`409` sinon).

## API interne (`/api/**`)

JSON en `snake_case`, erreurs RFC 9457, liste paginée du contrat. L'objet d'un autre répond `404`.

| Route | Rôle |
|---|---|
| `GET /api/demarche` | Présentation de la démarche (lue chez Sira, dernière copie servie si Sira ne répond pas). |
| `POST /api/parcours/demandes` | `{canal, identifiant_unique}` (exigé pour les trois canaux) → `201` `{id, canal, etat, url_accord, code_court, code_verification, expire_le, dossier_ouvert}`. |
| `GET /api/parcours/demande` | La demande en cours de ce navigateur. |
| `POST /api/parcours/retour` | `{demande, code_retour}` : retour du canal `REDIRECTION`. |
| `DELETE /api/parcours/demande` | Abandon : la demande est annulée chez Sira. |
| `GET /api/parcours/dossier` | Le dossier de ce navigateur. |
| `GET /api/parcours/dossier/pieces/{jeu}?apercu` | La pièce reçue pour ce jeu (`apercu=true` : PDF `inline`). |
| `PUT /api/parcours/dossier/declarations/{code}` | `{valeur}` : saisie libre (nature `TEXTE`, 120 caractères) → le dossier. |
| `PUT /api/parcours/dossier/declarations/{code}/piece?nom` | Corps `application/pdf`, 2 Mio au plus, signature `%PDF-` → le dossier. |
| `GET /api/parcours/dossier/declarations/{code}/piece?apercu` | La pièce jointe par la personne. |
| `DELETE /api/parcours/dossier/declarations/{code}` | Retire une déclaration → le dossier. |
| `POST /api/parcours/dossier/depot` | « Déposer mon dossier » : `depose_le` est posé, les déclarations sont figées (`409 dossier-depose` ensuite). |
| `DELETE /api/parcours` | Fin du parcours : la session est détruite. |
| `GET /api/agent/dossiers?page&taille&etat` | Liste paginée. |
| `GET /api/agent/dossiers/{id}` | `{dossier, demandes}`. |
| `GET /api/agent/dossiers/{id}/pieces/{jeu}?apercu` | La pièce reçue. |
| `GET /api/agent/dossiers/{id}/declarations/{code}/piece?apercu` | La pièce jointe par la personne. |
| `POST /api/agent/dossiers/{id}/verifications` | Redemande : `201`, ou `409 demande-deja-envoyee`. |

Les routes de session des agents (`/api/session`, `/api/auth/**`) sont celles du socle.

`GET /api/demarche` donne aussi, pour chaque jeu, `registre` et `champs` (niveau `CHAMPS`, depuis
`sira.formulaire.champs`), et le formulaire du service : `declarations[{code, libelle, aide, nature}]`
avec `nature` `TEXTE` ou `PIECE`.

Dossier : `{id, numero, etat, cree_le, modifie_le, depose_le, verifications[], declarations[], pieces_evitees[]}`,
où `etat` vaut `EN_VERIFICATION`, `COMPLET` ou `A_EXAMINER`, chaque vérification
`{jeu, libelle, niveau, registre, etat, valeur, champs, piece{nom,type,taille}, modifiee_le}` et
chaque déclaration `{code, libelle, nature, remplace, valeur, piece, modifiee_le}` (`remplace` : pièce
fournie à la place d'une information non transmise ; `code` est alors le jeu).

## Règles de sécurité et leurs preuves

Chaque règle a un test qui prouve le refus (`guichet-agent/serveur/src/test`).

| Règle | Preuve |
|---|---|
| Session anonyme liée à la demande, cookie `HttpOnly` ; le dossier ne s'affiche que sur le navigateur qui l'a déposé | `ParcoursTest` : autre session → `404` |
| Une nouvelle démarche dans le même navigateur fait oublier le dossier de la précédente (poste partagé) | `ParcoursTest` : dossier précédent `404` |
| Le dossier ne s'ouvre qu'après un accord attendu et donné ; un accord d'office ne l'ouvre pas | `ParcoursTest` : `dossier_ouvert` faux, `/dossier` et la pièce → `404` |
| Retour `REDIRECTION` strict : la demande doit être celle de la session, le code est vérifié par Sira | `ParcoursTest` : autre demande `404`, mauvais code `403`, lien ouvert dans un autre navigateur `404` |
| Jeton CSRF sur toute requête qui modifie | `ParcoursTest`, `AgentTest` : `403` |
| Saisies validées champ par champ, corps borné | `ParcoursTest` : `400` par champ, `413` |
| Limite de débit par navigateur et pour la plateforme | `ParcoursTest` : `429` ; `LimiteDeDebitTest` |
| L'identifiant unique est exigé pour les trois canaux et n'est jamais enregistré | `ParcoursTest` : `400` sans identifiant, absent de la base et de la session |
| Une information reçue ou attendue d'un registre ne se déclare jamais ; seule une information non transmise se remplace par une pièce | `DeclarationsTest` : `409 information-du-registre` |
| Pièce : PDF seulement (type et signature `%PDF-`), 2 Mio, nom assaini, dossier de la session seulement ; 256 Kio sur toutes les autres routes | `DeclarationsTest` : `400`, `413`, `415`, autre session `404` ; `NomDeFichierTest` |
| Rien ne change après le dépôt | `DeclarationsTest` : `409 dossier-depose` |
| Pièces servies en `attachment` (`inline` en aperçu pour un PDF seulement), `nosniff`, `no-store` | `DeclarationsTest` |
| Une attestation qui n'est pas pour ce service, qui nomme un autre citoyen, ou que signe un autre registre que celui du jeu, est écartée | `ParcoursTest`, `AgentTest` |
| Aucun détail technique dans les erreurs | `ParcoursTest`, `DemarcheIndisponibleTest` |
| Agents : authentification, et administration égale au service | `AgentTest` : `401`, `403` |
| Appels sortants en mTLS sous l'identité `urn:sira:service:<code>`, vers la seule `urn:sira:socle:passerelle` | fausse passerelle des tests, qui exige le certificat client |
| Seules les routes prévues existent | `DemarcheTest` |

La vérification des attestations, le mTLS, la connexion OIDC et le CSRF sont ceux de
`commun` et `passerelle/kit` : le module n'en réécrit aucun.

## Choix de conception

- **Pseudonyme seul.** La table `dossier` porte le pseudonyme du citoyen pour ce service ; un même
  citoyen qui revient retrouve son dossier. Aucune identité n'est stockée.
- **Relance.** Une tâche périodique (`sira.service.relance`) reprend les demandes `PARTIELLE` et
  celles ouvertes par un agent, jusqu'à leur état final. Le code de retour est conservé le temps
  de ces relances, puis effacé.
- **Formulaire.** `sira.formulaire.declarations` (par profil) liste ce que la personne fournit
  elle-même ; `sira.formulaire.champs` donne les champs de chaque jeu `CHAMPS` (la passerelle ne
  les expose pas ; `ProfilsTest` vérifie l'accord avec l'amorçage). Les pièces jointes sont gardées
  en base (`declaration.piece`, 2 Mio au plus), jamais sur disque.
- **Aperçu.** Une pièce se prévisualise dans un nouvel onglet (`?apercu=true`, `inline`) : l'intégrer
  dans la page demanderait d'assouplir `frame-ancestors 'none'` et `object-src 'none'`, ce qui n'est
  pas fait.
- **Pièces évitées.** Elles se déduisent des registres qui ont répondu
  (`sira.service.pieces.<registre>`).
- **QR code.** Tracé en SVG par `qrcode-generator` 2.0.4 (licence MIT), seule dépendance ajoutée à
  `interfaces/demo` ; aucun style en ligne, la politique de sécurité de contenu reste stricte.
- **Thème.** `/config.json` (`nom`, `organisme`, `theme`) est servi par l'image web ; les écrans
  n'emploient que les composants et jetons de `@sira/ui`.

## Configuration

Profil Spring `concours`, `bourses` ou `entreprises` (`SPRING_PROFILES_ACTIVE`), qui fixe
`sira.service.code`, `nom`, `organisme`, `demarche` (`concours-direct`, `bourse-etudes`,
`immatriculation-commercant`) et `prefixe-dossier` (`CD`, `BE`, `RC`). `ProfilsTest` vérifie que
chaque profil est valide et s'accorde avec l'amorçage de la passerelle.

| Propriété | Rôle |
|---|---|
| `spring.datasource.url`, `.username`, `.password` | Base PostgreSQL du service (schéma créé par Flyway). |
| `sira.passerelle.url` | Adresse mTLS de la passerelle. |
| `sira.pki.repertoire` | Clés et certificats du membre `service-<code>`. |
| `sira.oidc.emetteur`, `.url-interne`, `.url-publique` | Annuaire des agents et adresse publique de la plateforme. L'adresse de retour déclarée chez Sira est `<url-publique>/retour`. |
| `sira.service.relance` | Intervalle des relances (10 s par défaut). |
| `sira.service.demandes-par-minute`, `-total` | Limites de débit (5 par navigateur, 120 pour la plateforme). |

Le serveur écoute en HTTP sur 8080 derrière le nginx de l'interface, la supervision sur 8081.

## Vérifier

```sh
./mvnw -q -ntp verify -pl guichet-agent/serveur          # parcours complets, fausse passerelle mTLS, PostgreSQL réel
cd interfaces/demo && npm run bibliotheques && npx ng build portail-service && npx eslint projects/portail-service
scripts/sira tester --grep "formulaire de la démarche"   # le parcours complet, dans un navigateur
```
