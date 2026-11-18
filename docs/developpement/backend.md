# Écrire un composant Java de Sira

Tout composant Java repose sur `commun` (`bf.sira.commun`). Un service qui se raccorde à Sira
ajoute `passerelle/kit` (`bf.sira.kit`). Le [contrat d'architecture](../architecture/CONTRAT.md) reste la règle.

## Construire et tester

```sh
./mvnw verify                      # compile, teste, contrôle la mise en forme
./mvnw spotless:apply              # remet le code en forme (google-java-format, style AOSP)
./mvnw -pl passerelle/serveur -am verify
docker build -f deploiement/docker/java.Dockerfile --build-arg MODULE=passerelle/serveur -t sira/passerelle .
```

Java 21, Spring Boot 4.1 ; Maven vient du wrapper. L'image tourne sous l'utilisateur `10001:10001`,
accepte un système de fichiers en lecture seule avec `/tmp` en `tmpfs`, et sa sonde de santé lit
`http://127.0.0.1:8081/actuator/health/readiness`.

## Ajouter un composant

1. Créer `<dossier>/pom.xml` (parent `bf.sira:sira`, `<finalName>app</finalName>`, plugin
   `spring-boot-maven-plugin`) et le déclarer dans les `<modules>` du `pom.xml` racine, ainsi que
   dans les `COPY` de `deploiement/docker/java.Dockerfile`.
2. Dépendre de `bf.sira:sira-commun` (versions gérées par le parent). Ajouter au besoin
   `spring-boot-starter-security-oauth2-resource-server` (jetons d'utilisateur) ou
   `spring-boot-starter-security-oauth2-client` (connexion avec session).
3. Choisir le mode réseau dans `application.yaml` :

| `sira.mtls.mode` | Serveur | Usage |
|---|---|---|
| `complet` (défaut) | HTTPS 8443, TLS 1.3, certificat client exigé | passerelle, connecteur |
| `client` | HTTP 8080 derrière son nginx, appels sortants en mTLS, cookie de session `HttpOnly` `SameSite=Lax` | espace citoyen, console, service de démonstration |
| `aucun` | rien de préréglé, aucune clé lue | registre simulé, tests |

Dans les modes `complet` et `client`, le port de gestion est `8081` sur `127.0.0.1`, et les corps
JSON sont en `snake_case` (aucune annotation à poser). Le mode par défaut est le plus strict : sans
clés, le composant ne démarre pas.

## Clés et certificats

Le répertoire `sira.pki.repertoire` (défaut `/run/sira/pki`) contient `ca.crt`, `tls.crt/.key`,
`cachet.crt/.key`, `chiffrement.crt/.key`, au format PEM (clés PKCS#8, EC P-256), lisibles par l'UID
10001. Seuls les fichiers utilisés sont lus. Conventions vérifiées par le code :

- chaque certificat porte un seul SAN de type URI, `urn:sira:<socle|service|registre>:<code>` ;
- `tls` : `extendedKeyUsage` serverAuth et clientAuth, et un SAN DNS au nom du conteneur (le client
  vérifie le nom du serveur) ;
- `cachet` : `keyUsage` digitalSignature, sans `extendedKeyUsage` ;
- `chiffrement` : `keyUsage` keyAgreement, sans `extendedKeyUsage`.

Une clé ne sert qu'à son usage : un certificat de transport est refusé pour signer ou chiffrer.

## Les briques

**Identité d'un membre** (`bf.sira.commun.identite`). L'identité vient du certificat client, jamais
d'un en-tête.

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    SecuriteMembres.appliquer(http); // X.509 → principal Membre, sans état, erreurs en JSON
    http.authorizeHttpRequests(a -> a
            .requestMatchers("/v1/demandes/**").hasAuthority(Autorites.SERVICE)
            .requestMatchers("/v1/requetes").hasAuthority(Autorites.PASSERELLE)
            .anyRequest().denyAll());
    return http.build();
}
```

Dans un contrôleur : `@AuthenticationPrincipal Membre membre`, puis `membre.identite()`.

**Jetons d'utilisateur** (`bf.sira.commun.jetons`). Une chaîne de filtres par royaume : un seul
émetteur, l'audience attendue, et le jeton doit être lié (`cnf.x5t#S256`) au certificat qui le
présente.

```java
@Bean @Order(1)
SecurityFilterChain citoyen(HttpSecurity http, JetonsRoyaume jetons) throws Exception {
    http.securityMatcher("/v1/citoyen/**");
    var regle = JetonsRoyaume.appliquer(http,
            jetons.decodeur(emetteurPublic, jwksInterne, "sira-passerelle"),
            IdentiteMembre.ESPACE_CITOYEN);
    http.authorizeHttpRequests(a -> a.anyRequest().access(regle));
    return http.build();
}
```

Les rôles du royaume deviennent `ROLE_<rôle>` ; le principal est le `Jwt`.

**Appels sortants** (`bf.sira.commun.mtls`).
`ClientsMtls.client("https://connecteur-etat-civil:8443", IdentiteMembre.registre("etat-civil"))`
rend un `RestClient` en mTLS (TLS 1.3, autorité Sira seule, pas de redirection, délais
`sira.http.delai-connexion` et `sira.http.delai-lecture`). Chaque client nomme le membre qu'il
joint : un serveur dont le SAN URI n'est pas cette identité est refusé pendant la poignée de main,
avant que rien ne parte. Keycloak se joint sous `IdentiteMembre.FOURNISSEUR_IDENTITE`.

**Signer et vérifier** (`bf.sira.commun.jose`). Beans disponibles : `Signataire`, `VerificateurJws`,
`ScelleurAttestation`, `LecteurAttestation`, `RegistreJti`.

```java
String jws = signataire.signer(TypesJose.REQUETE, revendications);               // passerelle
JWTClaimsSet requete = verificateur.verifier(jws, Attentes.requete(monRegistre)); // connecteur
var destinataire = scelleur.destinataire(certificatChiffrementPem, service);     // connecteur, avant le registre
String jwe = scelleur.sceller(attestation, destinataire);                        // connecteur
JWTClaimsSet contenu = lecteur.ouvrir(jwe, IdentiteMembre.registre("etat-civil")); // service
```

Un refus lève `JoseRefuse` (rendu en `403` sans détail). `aud` doit être exactement le
vérificateur : un objet à plusieurs audiences est refusé. Le `RegistreJti` fourni est en mémoire :
un composant à plusieurs instances déclare son propre bean, adossé à sa base.

**Erreurs** (`bf.sira.commun.erreurs`). Toutes les erreurs sortent en `application/problem+json`,
sans message technique. Pour une erreur métier :
`throw new ErreurMetier(HttpStatus.FORBIDDEN, "habilitation-absente", "Habilitation absente")`.
Un objet absent ou appartenant à un autre : `throw ErreurMetier.introuvable()`. La taille des corps
reçus est bornée par `sira.http.taille-corps-max` (256 Ko).

**Pseudonymes et secrets** (`bf.sira.commun.crypto`). `Pseudonymes.depuisFichier(chemin)` puis
`calculer(secteur, identifiantUnique)` ; `TempsConstant.egaux(a, b)` pour comparer un code ou un
jeton.

**Connexion avec session** (`bf.sira.commun.oidc`), en mode `client` avec le starter
`oauth2-client` :

```yaml
sira:
  mtls.mode: client
  oidc:
    client-id: sira-espace-citoyen
    emetteur: http://identite.sira.localhost:8600/realms/sira-citoyens   # vu du navigateur
    url-interne: https://keycloak:8443/realms/sira-citoyens              # vu du serveur, en mTLS
    url-publique: http://citoyen.sira.localhost:8600
```

```java
@Bean
SecurityFilterChain securite(HttpSecurity http, ConnexionOidc connexion) throws Exception {
    connexion.appliquer(http);
    http.authorizeHttpRequests(a -> a.anyRequest().authenticated());
    return http.build();
}
```

Le client s'authentifie par son certificat (`tls_client_auth`), sans secret. Routes fournies, toutes
sous `/api/` : `GET /api/auth/connexion/sira[?retour=/chemin]`, `GET /api/auth/retour`,
`POST /api/auth/deconnexion` (répond `{"url": …}` à suivre), `GET /api/session` (`nom`, `roles`).
Sans session, l'API répond `401` en JSON. Le cookie `XSRF-TOKEN` est à renvoyer dans l'en-tête
`X-XSRF-TOKEN`. Pour appeler la passerelle au nom de l'utilisateur : injecter `RelaisPasserelle` et
utiliser `client()`, qui présente le certificat du serveur et le jeton d'accès de la session.

## Raccorder un service avec le kit

Dépendance `bf.sira:sira-kit`, mode `client` ou `complet`, adresse `sira.passerelle.url` (défaut
`https://passerelle:8443`). Injecter `ClientSira` :

```java
DemandeCreee demande = sira.creerDemande(
        NouvelleDemande.parRedirection("concours-direct", retourUrl), cleIdempotence);
EtatDemande etat = sira.lireDemande(demande.id(), codeRetour);
Demarche demarche = sira.decrireDemarche("concours-direct");
for (Attestation attestation : sira.attestations(etat, demarche)) {
    switch (attestation.resultat()) {
        case Resultat.Statut statut -> …
        case Resultat.Champs champs -> …
        case Resultat.Document document -> …
    }
}
sira.annulerDemande(demande.id());
```

Les attestations sont déchiffrées avec la clé de chiffrement du service, puis vérifiées : signées par
le registre qui détient le jeu, pour ce service seul, sur le pseudonyme de la demande, pour cette
démarche, ce jeu et ce niveau (contrat §8). Pour un seul jeu : `sira.ouvrir(etat, demarche, jeu)`,
vide tant que rien n'est arrivé. Une attestation déplacée d'une autre demande lève
`AttestationInvalide`, une signature ou un destinataire refusés `JoseRefuse`. Un refus de la
passerelle lève `ErreurPasserelle` (`statut()`, `type()`).

## Tester

`commun` publie une PKI de test (dépendance `sira-commun`, `type` `test-jar`, portée `test`,
avec `bcpkix-jdk18on`) :

```java
PkiDeTest pki = PkiDeTest.creer();
Path cles = pki.ecrire(repertoire, IdentiteMembre.PASSERELLE);   // ca.crt, tls, cachet, chiffrement
RestClient client = new ClientsMtls(pki.bundle(pki.tls(IdentiteMembre.service("demo"))),
        Duration.ofSeconds(2), Duration.ofSeconds(5), RestClient::builder).client(url);
```

Avec `@SpringBootTest`, passer `sira.mtls.mode` dans `properties` et le répertoire par
`@DynamicPropertySource` (`sira.pki.repertoire`). `FauxFournisseur` simule le fournisseur d'identité
(clés publiques et point de jetons en mTLS) et fabrique des jetons d'accès liés à un certificat.
Aucune clé n'est écrite dans le dépôt.
