# Infrastructure

Tout ce qui fait tourner Sira sur un poste est décrit dans `deploiement/` et piloté par `scripts/sira`
(`scripts/sira aide` liste les commandes).
Rien de secret n'est dans le dépôt : l'autorité de certification, les certificats et les mots de passe
sont créés au premier démarrage par le conteneur `pki`, dans des volumes Docker.

## Commandes

| Commande | Effet |
|---|---|
| `scripts/sira demarrer` | construit, démarre le **socle seul**, attend que tout soit sain |
| `scripts/sira demo` | idem avec la démonstration (comptes fictifs, téléphone simulé, administrations simulées) |
| `scripts/sira arreter` | arrête en conservant les données |
| `scripts/sira etat` / `journaux [service]` | état, journaux |
| `scripts/sira comptes` | mot de passe commun des comptes de démonstration |
| `scripts/sira totp-reinitialiser <compte>` | efface l'application d'authentification d'un administrateur : nouveau QR code à sa prochaine connexion |
| `scripts/sira verifier` | tests de refus sur la pile qui tourne : ce qui doit rester fermé l'est |
| `scripts/sira tester` | tests de bout en bout (Playwright, Node 24) contre la pile qui tourne |
| `scripts/sira citoyens importer <fichier>` | ajoute des personnes fictives aux registres simulés ([demo-registres.md](../composants/demo-registres.md)) |
| `scripts/sira etranger [arreter]` | lance ou retire l'exemple de registre raccordé ([brancher-un-registre.md](../brancher-un-registre.md)) |
| `scripts/sira pki lister` | certificats émis et échéances |
| `scripts/sira pki emettre <code>` | émet les certificats d'un membre ajouté à `membres*.conf` |
| `scripts/sira bases` | crée les rôles et bases ajoutés à `secrets*.conf` après le premier démarrage |
| `scripts/sira reinitialiser` | supprime données, certificats et secrets |

Un seul port est publié : `SIRA_PORT` (8600), sur `127.0.0.1`. Les écrans sont servis par nom d'hôte,
tous en `.sira.localhost` : `citoyen`, `console` et `identite` pour le socle ; la démonstration ajoute
`telephone` (messages simulés), `concours`, `bourses`, `entreprises`, `etat-civil`, `nationalite`,
`casier`, `diplomes`. Sur le socle seul, ces noms répondent 404.

Les volumes gardent le mode qui les a créés (`socle` ou `demo`) : `demarrer` refuse des volumes de
démonstration et `demo` des volumes du socle, avec un message. Passer de l'un à l'autre demande
`scripts/sira reinitialiser`. Hors démonstration, Keycloak refuse aussi de démarrer si un fichier de
comptes ou de royaume fictif lui est monté, ou un secret `SIRA_DEMO_*`.

## Ce qui tourne

| Fichier | Conteneurs |
|---|---|
| `compose.yaml` (socle, démarre seul) | `pki` (s'arrête après usage), `socle-db`, `keycloak`, `acces-identite`, `passerelle`, `espace-citoyen` + `web-citoyen`, `console` + `web-console`, `proxy` |
| `compose.demo.yaml` | `demo-db` ; `mailpit` ; par registre `<r>` (`etat-civil`, `nationalite`, `casier`, `diplomes`) : `connecteur-<r>`, `registre-<r>`, `web-<r>` ; par service `<s>` (`concours`, `bourses`, `entreprises`) : `service-<s>`, `web-<s>` ; `web-telephone` ; dans le profil `etranger`, lancé seulement par `scripts/sira etranger` : `registre-etranger`, `connecteur-etranger` |

La démonstration ajoute aussi à la passerelle son amorçage (`passerelle/amorcage-demo/complet.yaml`,
importé une seule fois, base vide) et la liste des personnes fictives admises pour l'essai d'une
question. Les adresses de retour de l'amorçage s'écrivent `${SIRA_PORT:8600}` : la passerelle les
remplace à l'import.

Limites : chaque conteneur est borné en mémoire (`mem_limit` ; 512 Mo par JVM, 768 Mo pour la
passerelle, 448 Mo pour la démonstration, ramasse-miettes série) et en processus (`pids_limit` : 256,
512 pour les bases). Les journaux Docker tournent (`json-file`, 3 fichiers de 10 Mo). La pile
complète tient dans 6 à 7 Go.

Versions : chaque image est épinglée par empreinte (`@sha256:`) dans les fichiers Compose et les
Dockerfile, pas dans `.env`. Le lanceur Maven vérifie l'empreinte de sa distribution
(`.mvn/wrapper/maven-wrapper.properties`) ; les écrans s'installent par `npm ci --ignore-scripts`.

## Variables (`deploiement/.env`, modèle : `.env.example`)

| Variable | Défaut | Rôle |
|---|---|---|
| `SIRA_PORT` | `8600` | port publié ; fait partie des adresses publiques et de l'émetteur des jetons |
| `SIRA_ADRESSE` | `127.0.0.1` | adresse d'écoute du port publié |
| `SIRA_HTTPS` | `false` | `true` quand un frontal TLS est placé devant le proxy : HSTS, cookies `Secure`, `X-Forwarded-Proto: https` |
| `SIRA_MESSAGERIE_HOTE`, `_PORT`, `_STARTTLS`, `_SSL`, `_AUTH`, `_UTILISATEUR`, `_MOT_DE_PASSE`, `_EXPEDITEUR` | vide (aucun envoi) | serveur SMTP qui porte le code de connexion du citoyen, hors démonstration |

Les fichiers Compose se choisissent par `scripts/sira demarrer` ou `demo`, pas par `COMPOSE_FILE`.

Changer `SIRA_PORT` après le premier démarrage impose `scripts/sira reinitialiser` : les adresses de
retour des royaumes et de l'amorçage de la passerelle sont fixées à l'import.

`telephone.sira.localhost` n'existe qu'en démonstration : le proxy y sert l'écran `web-telephone` et
ne relaie de Mailpit que `GET /messagerie/search` (recherche, en lecture). Toute autre méthode répond
403, tout autre chemin de Mailpit 404 (ni interface, ni WebSocket, ni suppression).

## Conventions à l'intérieur d'un conteneur

| Chemin | Contenu |
|---|---|
| `/run/sira/pki/ca.crt` | autorité Sira, seule ancre de confiance |
| `/run/sira/pki/{tls,cachet,chiffrement}.{crt,key}` | les trois paires du membre (ECDSA P-256, PEM, clé PKCS#8) |
| `/run/sira/secrets/<fichier>` | secrets du composant, un fichier par secret |

Le sujet d'un certificat est `CN=<uri>,OU=<usage>,O=Sira` ; l'identité qui fait foi est le SAN URI
(`urn:sira:...`). Les clés ne sont lisibles que par l'utilisateur du conteneur : `10001` pour les
applications Java. Les certificats durent 90 jours et sont réémis au démarrage s'il leur reste moins
de 30 jours.

Bases : `jdbc:postgresql://socle-db:5432/<role>?sslmode=verify-full&sslrootcert=/run/sira/pki/ca.crt`
(`demo-db` pour la démonstration), utilisateur `<role>`, mot de passe dans
`/run/sira/secrets/base.motdepasse`. Avec Spring Boot : `SPRING_CONFIG_IMPORT=configtree:/run/sira/secrets/`,
puis `SPRING_DATASOURCE_PASSWORD=$${base.motdepasse}` dans le fichier Compose.

Identité (un royaume `<r>` parmi `sira-citoyens`, `sira-socle`, `demo-agents`) :

| Usage | Adresse |
|---|---|
| émetteur (`iss`) | `http://identite.sira.localhost:${SIRA_PORT}/realms/<r>` |
| autorisation (navigateur) | émetteur + `/protocol/openid-connect/auth` |
| jeton, depuis un conteneur, en mTLS | `https://keycloak:8443/realms/<r>/protocol/openid-connect/token` |
| introspection, révocation, `userinfo` | même préfixe, en mTLS |
| clés de signature | `https://keycloak:8443/realms/<r>/protocol/openid-connect/certs` |

Ces adresses se configurent explicitement : la découverte automatique, faite depuis un conteneur,
ne peut pas atteindre l'adresse publique. Les clients s'authentifient par leur certificat `tls`
(`tls_client_auth`), les jetons d'accès portent `cnf.x5t#S256`, PKCE S256 est obligatoire.
`keycloak:8443` est `acces-identite`, un relais qui termine le TLS mutuel et ne laisse passer que les
adresses ci-dessus (et la découverte). Son certificat porte l'identité du fournisseur,
`urn:sira:socle:identite` : les serveurs refusent tout autre interlocuteur pour leurs jetons et
leurs clés ; Keycloak lui-même n'écoute qu'en HTTP sur le réseau
`identite`, sous le nom `fournisseur-identite`, que seuls le proxy et ce relais rejoignent. Les
adresses de retour des clients sont exactes (`.../api/auth/retour`), sans joker.

## Ajouter un membre

1. Une ligne dans `deploiement/membres.conf` (socle) ou `membres.demo.conf` (démonstration) :
   `service-impots  urn:sira:service:impots  service-impots  10001`.
2. Ses secrets dans `secrets.conf` ou `secrets.demo.conf`, par exemple une base :
   `base-service-impots  hex  demo-db/roles/service_impots:70  service-impots/base.motdepasse:10001`.
3. `scripts/sira pki emettre service-impots`, puis `scripts/sira bases` si une base a été déclarée.

Pour un essai sans rien déclarer : `scripts/sira pki emettre <code> <uri> <dns> [uid]`.

## Ajouter un service Java

Dans `compose.yaml` (socle) ou `compose.demo.yaml` (démonstration), après avoir déclaré le membre :

```yaml
  service-impots:
    <<: *java          # durci, utilisateur 10001, /tmp en mémoire, mem_limit, sonde de santé
    build:
      context: ..
      dockerfile: deploiement/docker/java.Dockerfile
      args: { MODULE: guichet-agent/impots }
    image: sira/service-impots:local
    environment:
      <<: *java-env    # options de la JVM
      SPRING_CONFIG_IMPORT: configtree:/run/sira/secrets/
      SPRING_DATASOURCE_URL: jdbc:postgresql://demo-db:5432/service_impots?sslmode=verify-full&sslrootcert=/run/sira/pki/ca.crt
      SPRING_DATASOURCE_USERNAME: service_impots
      SPRING_DATASOURCE_PASSWORD: $${base.motdepasse}
    volumes:
      - { type: volume, source: pki, target: /run/sira/pki, read_only: true, volume: { subpath: membres/service-impots } }
      - { type: volume, source: secrets, target: /run/sira/secrets, read_only: true, volume: { subpath: service-impots } }
    networks: [sira, adm-impots]
    depends_on:
      pki: { condition: service_completed_successfully }
      demo-db: { condition: service_healthy }
```

Réseaux : `sira` pour parler à la passerelle et à Keycloak en mTLS ; `adm-<code>` (à déclarer
`internal: true` et à ajouter à `demo-db`) pour la base et l'écran du service ; `donnees` pour un
composant du socle qui utilise `socle-db`. Un service ne publie jamais de port. L'ancre `x-java`
lui donne une sonde de santé (`/actuator/health/readiness` sur `127.0.0.1:8081`), que
`scripts/sira demarrer` attend. Keycloak n'est pas une dépendance de démarrage : les adresses OIDC
sont configurées, rien n'est découvert au lancement.

## Ajouter un écran web

```yaml
  web-impots:
    <<: *web           # durci, /tmp en mémoire, sonde /sante
    build:
      context: ..
      dockerfile: deploiement/docker/web.Dockerfile
      args: { ESPACE: interfaces/demo, APPLICATION: portail-service }
    image: sira/web-portail:local
    environment:
      API_AMONT: http://service-impots:8080   # seul amont de /api/
      CONFIG_NOM: Quitus fiscal               # CONFIG_* → /config.json
      CONFIG_THEME: concours
    networks: [entree, adm-impots]
```

Le conteneur s'appelle `web-<nom>` et écoute en HTTP sur 8080. Pour l'exposer sur
`<nom>.sira.localhost`, ajouter son nom à la table `map $host $amont` de
`deploiement/proxy/sira.conf.template` (socle), ou à la table `map $host $amont_demo` et au `server_name` de
`deploiement/proxy/demo.conf.template` (démonstration). Tant que le conteneur est absent, le proxy répond par une
page 503.

## Ajouter un royaume, un client, un compte

- Royaume du socle : `identite/royaumes/<nom>-realm.json` (dans l'image). Royaume ou comptes de
  démonstration : `identite/demo/`, monté dans `/opt/keycloak/royaumes/` par `compose.demo.yaml`
  (`<royaume>-users-0.json` pour des comptes).
- Client d'un membre : `clientAuthenticatorType: client-x509`, attributs
  `x509.subjectdn: CN=<uri>,OU=tls,O=Sira`, `x509.casubjectdn: CN=AC Sira,O=Sira`,
  `tls.client.certificate.bound.access.tokens: true`, `pkce.code.challenge.method: S256`.
- `${SIRA_PORT}` et tout fichier `identite/import/SIRA_<NOM>` déclaré dans `secrets*.conf`
  (`${SIRA_<NOM>}`) sont remplacés à l'import.
- Un royaume n'est importé que s'il n'existe pas encore : après modification d'un fichier,
  `scripts/sira reinitialiser` puis `demarrer` (ou `demo`).

La console d'administration de Keycloak, le royaume `master`, le compte en ligne et
l'enregistrement de clients ne sont exposés ni par le proxy ni par le relais (404). Pour un diagnostic :
`docker compose exec keycloak /opt/keycloak/bin/kcadm.sh` avec le compte `amorcage`, dont le mot de
passe est dans `/run/sira/secrets/amorcage.motdepasse` du conteneur.

## Réseaux

| Réseau | Qui s'y trouve |
|---|---|
| `entree` | proxy, écrans `web-*` |
| `identite` (interne, adresses fixes) | Keycloak (HTTP 8080, alias `fournisseur-identite`), proxy, `acces-identite` |
| `base-identite` (interne) | Keycloak et `socle-db` |
| `sira` (interne, mTLS) | passerelle, connecteurs, serveurs des services, `acces-identite` (alias `keycloak`, 8443) |
| `donnees` (interne) | `socle-db` et ses clients |
| `app-citoyen`, `app-console` (internes) | un serveur d'application du socle et son écran, rien d'autre |
| `adm-<code>` (internes, démonstration) | une administration simulée : registre ou service, sa base (`demo-db`), son écran ; pour un registre, son connecteur (`adm-etranger` : le registre de l'exemple et son connecteur) |
| `idp-<code>` (internes, démonstration) | un registre simulé et `acces-identite`, pour l'annuaire de ses agents |
| `messagerie` (interne, démonstration) | Mailpit, Keycloak (envoi), proxy (recherche seule, pour le téléphone) |

Un registre simulé n'est joignable que par son connecteur et son écran ; la passerelle ne joint que
les connecteurs, en mTLS sur `sira`. Keycloak ne fait confiance aux en-têtes `X-Forwarded-*` que
du proxy et du relais (`KC_PROXY_TRUSTED_ADDRESSES`) ; son port de gestion n'écoute que sur sa
boucle locale. `socle-db` refuse toute connexion réseau du rôle `postgres`.

## Connexion du citoyen

Identifiant unique, puis code à six chiffres envoyé par la messagerie du royaume (une passerelle SMS
se branche dans `CodeUsageUnique.transmettre`). Un identifiant inconnu, désactivé ou verrouillé reçoit
la même page, à la même adresse de formulaire, dans le même délai (une seconde au moins), mais aucun
code. Le code vaut cinq minutes, dans le navigateur qui l'a demandé ; cinq essais en tout par
connexion, renvois compris ; trois renvois, espacés de 30 s ; dix codes par identifiant et par heure.
Keycloak verrouille en plus le compte après dix échecs, ou une minute après des échecs en rafale.

Le proxy freine les écrans de connexion par session (cookie `AUTH_SESSION_ID` : 5 requêtes/s, rafale
de 20) et par adresse (50/s, rafale de 100). Docker présente tous les clients locaux sous une même
adresse : sans frontal qui transmet la vraie, le second frein vaut pour tous.

## Journaux

Le proxy journalise méthode, hôte, chemin, statut, taille et durée, **sans chaîne de requête**
(ni code d'autorisation, ni `state`). Keycloak journalise ses événements de connexion et
d'administration (`LOGIN`, `LOGIN_ERROR`, ...), sans le détail des représentations ; ils sont aussi
conservés 30 jours dans sa base. `scripts/sira journaux keycloak proxy` les suit.

## Limites de la configuration fournie

Cette configuration sert un poste de développement ou de démonstration : HTTP sur des noms en
`.sira.localhost`, une autorité de certification créée sur le poste, des comptes fictifs dont les
secrets sont lisibles par `scripts/sira comptes`. Ce qu'il faut changer pour une production
(frontal TLS, autorité hors ligne ou en HSM, révocation, comptes nominatifs, hébergement) est
détaillé dans [securite.md](../securite.md#limites-et-mise-en-production).
