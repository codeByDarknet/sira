# Connecteur de registre

Module `consentement-minimisation/connecteur`. Un connecteur se pose devant le registre existant d'une administration et
le rend interrogeable par la passerelle, sans modifier ce registre. **Un seul binaire sert tous les
registres** : le code du registre, son adresse et sa politique locale sont de la configuration.

Le connecteur est le dernier mot de l'administration sur ses données : sa politique locale
s'applique avant toute réponse et l'emporte sur ce que la passerelle a habilité.

## Parcours d'une requête

`POST /v1/requetes`, corps `application/jose` (la requête signée par la passerelle, contrat §8),
réponse `200` `{"attestation": "<JWE>"}`.

1. **Appelant.** Connexion mTLS (TLS 1.3, certificat client exigé). Seule l'identité
   `urn:sira:socle:passerelle` est admise ; aucune autre route n'existe.
2. **Signature.** `VerificateurJws` de `commun` : ES256, chaîne jusqu'à l'autorité, SAN de la
   passerelle, type `sira-requete+jwt`, `aud` = ce registre, durée de vie de 60 s au plus, `jti` à
   usage unique (table `jti_vu`, partagée entre instances). Tout refus répond `403` sans détail.
3. **Lecture.** Le contenu est validé champ par champ (formes, longueurs) : sinon `400`.
4. **Politique locale.** Le jeu demandé est confronté à la politique **avant** tout appel au
   registre : un refus répond `422` et le registre n'est pas interrogé. Le certificat de
   chiffrement du demandeur est lui aussi validé avant (étape 7) : refusé, il répond `403` sans
   que le registre soit consulté.
5. **Registre existant.** Lecture décrite par le fichier de raccordement (plus bas) : adresse,
   chemin, méthode, authentification, puis traduction de la réponse vers les noms de champs des
   questions. Délais et tailles bornés, sans suivre de redirection ; HTTPS exigé hors
   démonstration, adresses internes refusées sauf hôte permis. Toute réponse inattendue devient
   « registre indisponible » (`503`) : rien de la réponse brute ne sort du connecteur.
6. **Résultat.** Le moteur de règles produit le résultat du niveau demandé (voir plus bas).
7. **Scellement.** `ScelleurAttestation` de `commun` : JWS au cachet du registre, puis JWE
   pour le certificat de chiffrement du service, validé contre l'autorité et le SAN du demandeur
   nommé dans la requête signée. Le connecteur ne rend rien en clair.
   Le demandeur est un service (`urn:sira:service:<code>`), ou la passerelle elle-même
   (`urn:sira:socle:passerelle`) quand la console essaie une question sur une personne fictive :
   l'attestation d'essai est alors chiffrée pour la passerelle. Tout autre demandeur est refusé (`400`).
8. **Journal.** Une ligne est écrite avant de rendre l'attestation ; si l'écriture échoue,
   l'attestation n'est pas remise.

Au démarrage, le connecteur refuse de se lancer si la politique est absente ou invalide, ou si le
cachet monté n'est pas celui du registre configuré.

## Les trois niveaux

| Niveau | Demande (`jeu`) | `resultat` dans l'attestation |
|---|---|---|
| `STATUT` | `regle: {champ, operateur, valeur}` | `{"valeur": true}` ou `{"valeur": false}` |
| `CHAMPS` | `champs: [chemin, ...]` | `{"champs": {chemin: valeur}}` |
| `DOCUMENT` | `document: type` | `{"document": {nom, type, sha256, contenu}}` (contenu en base64) |

Le jeu porte aussi `code`, `niveau` et, facultatif, `validite` (durée ISO-8601, par exemple `P90D`).
Une demande qui mélange les niveaux est refusée.

Au niveau `STATUT`, **la valeur source ne sort jamais** : ni dans la réponse, ni dans le journal, ni
dans les traces applicatives, ni dans un message d'erreur. Les erreurs ne citent ni le champ ni son
contenu, et une réponse du registre existant n'est jamais recopiée.

### Opérateurs

| Opérateur | Valeur attendue | Vrai quand |
|---|---|---|
| `EGAL` | texte, nombre ou booléen | le champ a cette valeur (nombres comparés sans égard à l'écriture) |
| `AU_MOINS` | nombre ou date | le champ est supérieur ou égal |
| `AU_PLUS` | nombre ou date | le champ est inférieur ou égal |
| `AGE_AU_MOINS` | entier de 0 à 150 | l'âge en années révolues, à la date du jour, atteint ce seuil |
| `AGE_AU_PLUS` | entier de 0 à 150 | l'âge en années révolues ne dépasse pas ce seuil |
| `EXISTE` | aucune | le chemin désigne au moins une valeur |
| `AUCUN` | aucune | le chemin ne désigne aucune valeur (champ absent, `null` ou liste vide) |
| `ENCORE_VALIDE` | aucune | la date du champ est aujourd'hui ou plus tard |

Règles communes :

- un champ absent ou `null` ne satisfait aucune comparaison : la réponse est « non » ;
- quand le chemin traverse une liste, la règle est vraie dès qu'une valeur la satisfait ;
- une valeur d'un type inattendu (un texte là où la règle attend une date, une date de naissance
  dans le futur) fait refuser la demande (`422`) : le registre n'atteste pas ce qu'il ne sait pas lire ;
- les dates s'écrivent `AAAA-MM-JJ` (ou date et heure avec décalage) ; « aujourd'hui » est pris dans
  le fuseau `sira.connecteur.fuseau`. Une personne née un 29 février vieillit le 1er mars.

### Chemins de champ

Un chemin désigne une ou plusieurs valeurs de la fiche JSON rendue par le registre. Ce n'est pas un
langage d'expression : rien n'est évalué, il n'y a ni fonction ni opérateur.

```
date_naissance                 un champ
certificat.valide_jusqu_au     un champ d'un objet
condamnations                  une liste (EXISTE / AUCUN : y a-t-il un élément ?)
diplomes.annee                 le champ « annee » de chaque élément de la liste
diplomes[type=BAC]             les éléments dont le champ « type » vaut « BAC »
diplomes[type=BAC].mention     la mention de ces éléments
```

- segments séparés par `.`, cinq au plus, 128 caractères au total ;
- un nom : lettre ou `_`, puis lettres, chiffres ou `_` (64 caractères au plus) ;
- un filtre par segment, `[cle=valeur]`, égalité seulement ; la valeur est faite de lettres,
  chiffres, `_` ou `-` (64 au plus) ;
- une liste est toujours parcourue élément par élément ; il n'y a pas d'accès par position.

## Politique locale

Fichier YAML monté en lecture seule (`sira.connecteur.politique`, par défaut
`/etc/sira/politique.yaml`), lu une fois au démarrage. **Tout ce qui n'y figure pas est refusé.**

```yaml
validite:
  par_defaut: P90D     # durée d'une attestation quand la requête n'en propose pas
  maximale: P180D      # plafond appliqué à la durée proposée par la requête

champs:
  - chemin: date_naissance        # chemin sans filtre
    niveau: STATUT                # niveau maximal exposable : STATUT ou CHAMPS
    operateurs: [AGE_AU_MOINS, AGE_AU_PLUS]   # facultatif : questions admises
    valeurs: [15, 18, 21, 35]                 # facultatif : valeurs de comparaison admises
  - chemin: nom
    niveau: CHAMPS

documents:
  - extrait-naissance             # types de document que le registre accepte de remettre
```

- `niveau: STATUT` : le champ ne répond qu'à des questions par oui ou par non ; `niveau: CHAMPS` : sa
  valeur peut aussi être remise.
- `operateurs` et `valeurs` restreignent les questions : sans eux, un seuil d'âge variable
  permettrait de retrouver une date de naissance par approches successives.
- Un filtre `[cle=valeur]` n'est admis que si le champ filtré est lui-même exposé.
- Une clé inconnue, un chemin en double ou un fichier vide empêchent le démarrage.

Exemples complets : `consentement-minimisation/connecteur/src/test/resources/politiques/` (`etat-civil.yaml`,
`nationalite.yaml`, `casier.yaml`, `diplomes.yaml`). Ils illustrent le format ; la politique d'un
déploiement se rédige avec les noms de champs et de documents de son propre registre.

## Raccordement au registre

Fichier YAML monté en lecture seule (`sira.connecteur.registre.raccordement`), lu une fois au
démarrage ; une erreur empêche le démarrage. Guide pas à pas :
[brancher-un-registre.md](../brancher-un-registre.md).

```yaml
adresse: https://registre.exemple.bf          # schéma, hôte, port ; rien d'autre
fiche:
  methode: GET                                # GET (défaut) ou POST
  chemin: /api/v2/personnes/{identifiant}     # {identifiant} est encodé ; la requête est admise
  corps: '{"nin": "{identifiant}"}'           # POST seulement
  racine: /data/personne                      # facultatif : nœud qui porte la personne
document:                                     # facultatif : sans lui, aucun document
  chemin: /api/v2/personnes/{identifiant}/pieces/{type}
authentification:
  type: CLE_API                               # AUCUNE, JETON (Bearer), CLE_API, MTLS
  en_tete: X-Api-Key                          # CLE_API (défaut X-Api-Key)
  fichier: /run/sira/secrets/cle-api          # JETON, CLE_API : une ligne, jamais dans le dépôt
  # MTLS : certificat, cle, autorite (fichiers PEM montés)
correspondance:                               # nom des questions -> emplacement dans la réponse
  date_naissance: /naissance/date             # pointeur JSON (RFC 6901)
  nationalite: /nationalites/*/code           # « * » parcourt une liste
  prenoms: identite.prenoms[*]                # ou chemin simple, « [*] » pour une liste
```

Seuls les champs de la correspondance sont lus : la politique locale et le moteur de règles ne
voient que ces noms. Un emplacement absent vaut « champ absent ». Une réponse `404` vaut
« personne inconnue » ; toute autre réponse inattendue (statut, type, taille, racine qui n'est
pas un objet, délai) vaut « registre indisponible ».

Sans fichier, `sira.connecteur.registre.url` garde l'ancienne lecture
`GET {url}/interne/personnes/{identifiant}` sans traduction (registre de diplômes de la
démonstration). L'un ou l'autre, jamais les deux.

## Configuration

| Propriété (variable d'environnement) | Défaut | Rôle |
|---|---|---|
| `sira.connecteur.registre.code` (`SIRA_CONNECTEUR_REGISTRE_CODE`) | obligatoire | code du registre ; l'identité est `urn:sira:registre:<code>` |
| `sira.connecteur.registre.raccordement` (`SIRA_CONNECTEUR_REGISTRE_RACCORDEMENT`) | — | fichier de raccordement (voir plus haut) |
| `sira.connecteur.registre.url` (`SIRA_CONNECTEUR_REGISTRE_URL`) | — | ancienne forme, sans fichier : `http://registre-diplomes:8080` |
| `sira.connecteur.registre.http-admis` (`SIRA_CONNECTEUR_REGISTRE_HTTPADMIS`) | `false` | admet HTTP simple (démonstration, réseau privé) ; sinon HTTPS exigé |
| `sira.connecteur.registre.hotes-permis` (`SIRA_CONNECTEUR_REGISTRE_HOTESPERMIS`) | vide | hôtes admis même s'ils sont internes ; tout autre hôte doit n'avoir que des adresses publiques |
| `sira.connecteur.politique` (`SIRA_CONNECTEUR_POLITIQUE`) | `/etc/sira/politique.yaml` | fichier de politique locale |
| `sira.connecteur.fuseau` | `Africa/Ouagadougou` | fuseau de la « date du jour » |
| `sira.connecteur.registre.delai-connexion` | `500ms` | délai de connexion au registre (10 s au plus) |
| `sira.connecteur.registre.delai-lecture` | `2s` | délai de réponse du registre (10 s au plus ; la passerelle attend 3 s) |
| `sira.connecteur.registre.taille-fiche-max` | `256KB` | taille maximale d'une fiche |
| `sira.connecteur.registre.taille-document-max` | `2MB` | taille maximale d'un document |
| `spring.datasource.url`, `.username`, `.password` | obligatoires | base PostgreSQL du journal |
| `sira.connecteur.appelant` (`SIRA_CONNECTEUR_APPELANT`) | `passerelle` | qui peut ouvrir la liaison TLS : `passerelle`, seule valeur admise ; la requête est en plus un JWS signé par la passerelle |

Les clés et certificats du membre (`sira.pki.repertoire`, par défaut `/run/sira/pki`), le port
`8443` en mTLS et la sonde de santé (`http://127.0.0.1:8081/actuator/health/readiness`, boucle
locale) viennent des réglages communs de `commun`.

## Journal local

Table `journal`, en ajout seul (toute modification, suppression ou vidage est rejeté par la base).
Le schéma est créé au démarrage par Flyway.

| Colonne | Contenu |
|---|---|
| `horodatage`, `id_echange` | quand, et le `jti` de la requête |
| `demandeur`, `demarche`, `finalite` | quel service, pour quelle démarche et quelle finalité |
| `jeu`, `niveau`, `objet` | ce qui a été demandé : la règle (champ et opérateur, sans le seuil), les champs ou le type de document |
| `pseudonyme`, `autorisation`, `canal` | pour qui (jamais l'identifiant unique) et sous quel accord |
| `resultat` | l'issue : `REUSSI`, `CERTIFICAT_REFUSE` ou le motif du refus |

Le journal ne contient ni la valeur lue, ni la réponse (oui ou non), ni l'identifiant unique. Une
requête rejetée avant d'être authentifiée ou comprise (`403`, `400`) n'y figure pas : elle laisse
une trace applicative portant le seul motif.

## Erreurs

Réponses `application/problem+json`, type `urn:sira:erreur:<code>`.

| Statut | Code | Situation | Issue au journal |
|---|---|---|---|
| `400` | `requete-invalide` | contenu signé mais mal formé | aucune |
| `403` | selon `commun` | appelant autre que la passerelle, signature refusée, rejeu | aucune |
| `403` | selon `commun` | certificat de chiffrement du service refusé | `CERTIFICAT_REFUSE` |
| `404` | `personne-inconnue` | le registre ne connaît pas la personne ou n'a pas ce document | `PERSONNE_INCONNUE` |
| `413`, `415` | selon `commun` | corps de plus de 256 Ko, format autre que `application/jose` | aucune |
| `422` | `jeu-refuse` | politique locale ou règle inapplicable | `CHAMP_NON_EXPOSE`, `NIVEAU_DEPASSE`, `QUESTION_NON_AUTORISEE`, `DOCUMENT_NON_AUTORISE`, `JEU_INVALIDE`, `REGLE_INAPPLICABLE` |
| `503` | `registre-indisponible` | registre injoignable, lent, en erreur ou réponse illisible | `REGISTRE_INDISPONIBLE` |

Le motif précis d'un `422` reste dans le journal de l'administration : la réponse ne dit pas quel
champ est protégé.

## Déploiement

Une instance par registre, image construite par `deploiement/docker/java.Dockerfile` avec
`MODULE=consentement-minimisation/connecteur`.

```yaml
connecteur-etat-civil:
  environment:
    SIRA_CONNECTEUR_REGISTRE_CODE: etat-civil
    SIRA_CONNECTEUR_REGISTRE_RACCORDEMENT: /etc/sira/raccordement.yaml
    SIRA_CONNECTEUR_REGISTRE_HOTESPERMIS: registre-etat-civil
    SIRA_CONNECTEUR_REGISTRE_HTTPADMIS: "true"      # démonstration : réseau privé, HTTP simple
    SPRING_CONFIG_IMPORT: configtree:/run/sira/secrets/
    SPRING_DATASOURCE_URL: jdbc:postgresql://socle-db:5432/connecteur_etat_civil?sslmode=verify-full&sslrootcert=/run/sira/pki/ca.crt
    SPRING_DATASOURCE_USERNAME: connecteur_etat_civil
    SPRING_DATASOURCE_PASSWORD: $${base.motdepasse}
  volumes:
    - ./politiques/etat-civil.yaml:/etc/sira/politique.yaml:ro
    - ./raccordements/etat-civil.yaml:/etc/sira/raccordement.yaml:ro
    # plus les clés du membre (/run/sira/pki) et ses secrets (/run/sira/secrets), en lecture seule
```

Le connecteur n'a pas besoin que le registre existant réponde pour démarrer ; il lui faut sa base,
ses clés et sa politique. Le rôle de base doit pouvoir créer ses tables dans son schéma.

## Tests

`./mvnw verify -pl consentement-minimisation/connecteur` (Docker requis pour PostgreSQL).

| Test | Ce qu'il prouve |
|---|---|
| `CheminTest` | syntaxe des chemins, listes, filtres, entrées difformes refusées |
| `MoteurReglesTest` | les huit opérateurs, âge au jour près, 29 février, champs absents, listes vides, types inattendus |
| `PolitiqueTest` | niveaux, opérateurs et valeurs admis, documents, durées, politiques invalides, les quatre exemples |
| `ParcoursTest` | les trois niveaux de bout en bout (mTLS, base réelle, faux registre HTTP, autorité de test), validité, attestation illisible pour un tiers, journal sans valeur et en ajout seul, sonde de santé |
| `RefusTest` | chaque refus de sécurité : identité, certificat, route, signature, audience, expiration, rejeu, format, politique avant registre, certificat de chiffrement (refusé sans appeler le registre), `404`, `503`, aucune valeur dans les traces |
| `RegistreJtiBaseTest` | usage unique d'un `jti` tenu en base, oubli des identifiants échus, refus si la base ne répond pas |
| `CachetDuRegistreTest` | refus de démarrer avec le cachet d'un autre registre |
| `CorrespondanceTest` | pointeurs JSON et chemins simples, listes, échappements, entrées difformes refusées |
| `GardeAdressesTest` | adresses internes refusées (boucle locale, privées, lien local, IPv6 locales), hôtes permis |
| `ClientRegistreTest` | registre conçu autrement que ceux de Sira, simulé : clé d'API, URL avec paramètre, enveloppe, POST et jeton, `404`, réponses inattendues, HTTPS exigé, fichiers invalides |
