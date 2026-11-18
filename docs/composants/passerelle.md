# Passerelle

Module `passerelle/serveur`. La passerelle est le point de passage unique entre un service qui
demande une information et le registre qui la détient. Elle tient le référentiel (qui peut demander
quoi, pour quelle démarche), recueille l'accord du citoyen, interroge les connecteurs et écrit le
journal. Elle ne lit jamais une donnée : les attestations la traversent chiffrées pour le service.

Le processus assemble trois bibliothèques : `journal` (journal chaîné), `consentement-minimisation/consentement`
(accords, codes, pseudonymes, vues minimales) et `resilience` (disjoncteurs, file d'attente). Une
même transaction couvre ainsi l'accord, l'échange et sa ligne de journal. La passerelle fournit les
interfaces dont ces modules ont besoin (`Echanges`, `Reprise`) ; ils ne dépendent jamais d'elle.

**Le socle ne porte aucune donnée.** Membres, registres, jeux, démarches et habilitations sont
déclarés par la console, ou importés une fois au premier démarrage (voir « Amorçage »).

## Trois familles de routes, trois populations

| Préfixe | Qui appelle | Preuve exigée |
|---|---|---|
| `/v1/demandes/**`, `/v1/demarches/{code}` | un service | certificat mTLS d'un membre actif au rôle `SERVICE` |
| `/v1/citoyen/**` | l'espace citoyen | certificat de l'espace citoyen **et** jeton du royaume `sira-citoyens` |
| `/v1/admin/**` | la console | certificat de la console **et** jeton du royaume `sira-socle` |

Chaque préfixe a sa propre chaîne de filtres et un seul émetteur de jetons. Un jeton doit être lié
au certificat qui le présente (`cnf.x5t#S256`), porter l'audience `sira-passerelle` et l'émetteur
exact du royaume. Toute autre route est refusée. Les formes échangées sont celles de
[API.md](../architecture/API.md) et du [contrat](../architecture/CONTRAT.md).

Règles appliquées partout :

- l'identité de l'appelant vient du certificat ou du jeton, jamais du corps de la requête ;
- l'objet d'autrui n'existe pas : une demande, une autorisation ou une ligne de journal qui
  n'appartient pas à l'appelant répond `404` ;
- toute entrée est validée (formes, longueurs, clés inconnues refusées) : `400 VALIDATION` ;
- les erreurs sont des `application/problem+json` sans détail technique ;
- les rôles de la console : lecture pour `instructeur`, `autorite`, `auditeur` ; écriture pour
  `instructeur` ; approbation, rejet et suspension pour `autorite`, qui ne peut pas décider d'une
  habilitation qu'elle a elle-même instruite (`403 SEPARATION_DES_ROLES`).

## Référentiel et habilitations

Les ressources `/v1/admin/membres`, `/registres`, `/jeux`, `/demarches` se créent, se modifient et
se désactivent ; rien ne s'efface. Une habilitation suit `BROUILLON → SOUMISE → APPROUVEE` ou
`REJETEE`, puis éventuellement `SUSPENDUE`. Seule une habilitation `APPROUVEE` ouvre un jeu à un
service pour une démarche. Une suspension vaut aussitôt : les demandes déjà créées ne peuvent plus
être acceptées et ce qui attendait dans la file est refusé. Chaque changement est tracé au journal
avec son auteur.

`POST /v1/admin/jeux/essai` évalue un jeu, avant sa publication, sur une personne fictive. Seuls
les identifiants listés dans `sira.passerelle.essai.personnes-fictives` sont admis (liste vide :
aucun essai). La réponse ne contient que le niveau demandé.

## Demande, accord, collecte

`POST /v1/demandes` exige l'en-tête `Idempotency-Key` : la même clé rend la même demande, la même
clé avec un autre contenu répond `409`. Le service nomme la démarche, les jeux, le canal et son
certificat de chiffrement. Trois canaux :

| Canal | Le citoyen rejoint la demande par |
|---|---|
| `REDIRECTION` | `url_accord`, puis retour vers une adresse de `retours_autorises` du membre |
| `CODE` | le code court (ou le QR code) saisi dans l'espace citoyen ; pour accepter, il recopie le code de vérification affiché par le service |
| `NOTIFICATION` | son espace citoyen, où la demande apparaît à sa connexion ; le service le désigne par son identifiant (il recopie alors le code de vérification) ou par son pseudonyme |

Une demande expire après `sira.passerelle.demandes.duree-accord` (5 min). La décision ne se
prend qu'une fois (`409 DECISION_DEJA_PRISE`). Bornes, toutes sous `sira.passerelle.demandes` :

| Propriété | Défaut | Ce qu'elle borne |
|---|---|---|
| `essais-rattachement-max` | 5 | codes courts faux par citoyen pendant `duree-accord` |
| `essais-rattachement-global-max` | 1000 | codes courts faux, tous citoyens ensemble, pendant `duree-accord` |
| `essais-verification-max` | 5 | codes de vérification faux sur une demande ; ensuite, seul le refus reste possible |

Le compte et l'essai de code court se font sous un verrou consultatif PostgreSQL : des essais
simultanés ne dépassent pas la borne. Les codes ne sont gardés que sous forme d'empreinte HMAC
(clé de pseudonymisation), effacée à la décision ou à l'expiration.

Une demande désignée par identifiant unique (`NOTIFICATION`, ou `REDIRECTION` et `CODE` quand le
service le transmet) ne crée aucune fiche de citoyen : elle garde le pseudonyme interne calculé
(`designation`), et ne lui est remise qu'à sa connexion. Aucun autre citoyen ne peut s'y
rattacher, ni par le lien ni par le code court : pour lui, elle n'existe pas. La réponse au
service est la même, que la personne soit connue ou non. Sur `REDIRECTION` et `CODE`, la
désignation ne change rien d'autre : ni message, ni acceptation d'office, mêmes codes.

**Réutilisation.** Par le canal `NOTIFICATION` avec le pseudonyme (un agent authentifié du
service, à qui le pseudonyme n'a été remis qu'après un accord), si le citoyen a déjà une autorisation active pour
ce service, cette démarche, ces jeux et ce même certificat de chiffrement, la demande est acceptée
sans nouvel écran ; une attestation encore valide est resservie (ligne `REUTILISATION`), sinon le
registre est rappelé. Le citoyen retire une autorisation par `DELETE /v1/citoyen/autorisations/{id}` :
les attestations encore en dépôt sont détruites et plus rien n'est servi sous cet accord. Avant chaque envoi
à un registre, la collecte relit l'élément et l'autorisation : un retrait arrivé pendant les
tentatives arrête les suivantes.

**Ce que l'instructeur ne change pas seul.** Un jeu cité par une habilitation soumise, approuvée
ou suspendue, ou par une autorisation non retirée, est figé (`409`) : seul `actif` change. Les
`retours_autorises` d'un membre et l'`adresse` d'un registre se changent par une décision de
l'autorité (`PUT /v1/admin/membres/{code}/retours`, `PUT /v1/admin/registres/{code}/adresse`).

**Collecte.** Pour chaque jeu accepté, la passerelle signe une requête (JWS au cachet de la
passerelle, usage unique, 60 s) et l'envoie au connecteur du registre. Le service y est désigné par
un pseudonyme propre à son secteur : deux services ne peuvent pas rapprocher leurs usagers.
L'attestation revient scellée par le registre et chiffrée pour le service ; la passerelle la garde
en dépôt le temps que le service la relève (`GET /v1/demandes/{id}`), puis la purge.

## Résilience

- Chaque registre a son disjoncteur et sa cloison (Resilience4j) : un registre en panne ou lent
  n'affecte pas les autres. Seule une panne compte comme un échec ; un refus du registre est une
  réponse.
- L'essai immédiat est borné (`collecte.budget-immediat`). Ce qui n'a pas pu être obtenu reste dans
  une **file durable en base** (table `element`, relève par `FOR UPDATE SKIP LOCKED`, bail, attente
  qui double jusqu'à `file.delai-max`, abandon après `file.duree-max`). La demande est alors
  `PARTIELLE` et se complète seule au retour du registre ; plusieurs instances se partagent la file.
- `GET /v1/admin/etat` montre, par registre, le disjoncteur, la file et les derniers succès et
  échecs. L'état d'un disjoncteur est celui de l'instance qui répond.

## Transports

Un registre déclare son `transport`. `DIRECT` est fourni : mTLS de la passerelle au connecteur, qui
doit présenter l'identité `urn:sira:registre:<code>` du registre visé ; sinon la poignée de main
échoue, rien n'est envoyé et l'élément reste en file (`IdentiteConnecteurTest`).
Le point d'extension est l'interface `bf.sira.passerelle.collecte.Transport` : déclarer un autre
composant Spring de ce type suffit pour que les registres qui déclarent ce transport l'empruntent.
La signature de la requête et le chiffrement de l'attestation ne dépendent pas du transport.

## Journal

Table `journal`, en ajout seul : le rôle applicatif ne peut ni modifier ni supprimer une ligne
(déclencheurs en base). Chaque ligne porte le hachage de la précédente ; des points de contrôle
signés par la passerelle sont posés à intervalle régulier. `GET /v1/admin/journal/verification`
recalcule la chaîne et désigne la première rupture. Le citoyen y est désigné par un pseudonyme de
journal, jamais par son identifiant ; la console n'en voit qu'une forme tronquée. Le citoyen lit
ses lignes par `/v1/citoyen/journal` et peut en signaler une ; la console traite les signalements.

## Amorçage

Propriété `sira.amorcage.repertoire` : un répertoire, ou un fichier seul. Sans elle, rien n'est
importé. Au démarrage, **si la table des membres est vide**, les fichiers `*.yaml`, `*.yml` et
`*.json` du répertoire sont lus par ordre de nom et importés en une transaction ; un fichier
illisible ou invalide empêche le démarrage et n'importe rien. Dès que le référentiel contient un
membre, l'amorçage est ignoré : il ne sert qu'une fois.

Un fichier porte cinq sections facultatives, importées dans cet ordre, aux formes des ressources de
la console :

| Section | Clés |
|---|---|
| `membres` | `code`, `nom`, `organisme`, `roles` (`SERVICE`, `REGISTRE`), `retours_autorises` |
| `registres` | `code`, `nom`, `membre`, `adresse`, `transport` |
| `jeux` | `code`, `registre`, `niveau`, `libelle`, `description`, `validite`, et selon le niveau `regle`, `champs` ou `document` |
| `demarches` | `code`, `libelle`, `finalite`, `base_legale`, `service`, `duree_autorisation_jours` |
| `habilitations` | `service`, `demarche`, `jeux`, `justification`, `etat` |

- Les clés inconnues sont refusées. `identite` d'un membre est admise mais ignorée : elle découle
  du code et des rôles.
- `etat` d'une habilitation est facultatif et ne peut valoir que `APPROUVEE` ; il est tracé au
  journal comme une décision d'amorçage.
- Le YAML est lu sans instancier de type ; aucun secret n'a sa place dans ces fichiers.

Exemple complet : [`passerelle/serveur/src/test/resources/amorcage/exemple.yaml`](../../passerelle/serveur/src/test/resources/amorcage/exemple.yaml).

## Configuration

| Propriété | Défaut | Rôle |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://socle-db:5432/passerelle?sslmode=verify-full&…` | base de la passerelle (rôle `passerelle`) |
| `base.motdepasse` | fichier `/run/sira/secrets/base.motdepasse` | mot de passe de la base |
| `sira.pki.repertoire` | `/run/sira/pki` | clés et certificats du membre `urn:sira:socle:passerelle` |
| `sira.passerelle.pseudonymisation-cle` | `/run/sira/secrets/pseudonymisation.cle` | clé des pseudonymes |
| `sira.passerelle.identite.citoyens.emetteur` / `.cles` | royaume `sira-citoyens` | émetteur exact et adresse interne des clés |
| `sira.passerelle.identite.socle.emetteur` / `.cles` | royaume `sira-socle` | idem pour la console |
| `sira.passerelle.espace-citoyen-url` | `http://citoyen.sira.localhost:8600` | base de `url_accord` |
| `sira.amorcage.repertoire` | vide | répertoire ou fichier d'amorçage |
| `sira.passerelle.essai.personnes-fictives` | vide | identifiants admis pour l'essai d'un jeu |
| `sira.passerelle.collecte.*`, `.disjoncteur.*`, `.file.*`, `.purge.*`, `.journal.*`, `.demandes.*` | `Proprietes` (passerelle), `ReglageEssais`, `ReglageDisjoncteur`, `ReglageFile` (résilience), `ReglagesJournal` (journal), `ReglagesAccord` (consentement) ; clés inchangées | délais, seuils et fréquences |

Port `8443` en mTLS (TLS 1.3, certificat client exigé) ; sonde de santé sur `127.0.0.1:8081`
(`/actuator/health`). Le schéma est créé par Flyway au démarrage.

## Tests

`./mvnw verify -pl passerelle/serveur` lance les tests unitaires et les tests d'intégration : une
vraie base PostgreSQL (Testcontainers), une autorité de test, de faux connecteurs en mTLS qui
vérifient la requête signée et scellent de vraies attestations. Ils couvrent les trois canaux de
bout en bout, la panne d'un registre puis la reprise automatique, la réutilisation, le retrait,
l'altération du journal et chaque refus de sécurité.
