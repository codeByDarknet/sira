# Sécurité

Quand un service apprend qu'une personne est majeure sans voir sa date de naissance, il faut
pouvoir le prouver : que la passerelle n'a rien lu, que le registre n'a répondu qu'à une demande
acceptée, et que la consultation figure au journal sans pouvoir en être effacée. Cette page dit
contre quoi Sira se protège, où chaque mesure se trouve dans le code et quel test la vérifie.
Elle dit aussi ce que la configuration fournie ne fait pas.

Les règles normatives sont au §5 du [contrat](architecture/CONTRAT.md) ; le durcissement des
conteneurs et des réseaux est décrit dans [infrastructure.md](developpement/infrastructure.md).

## Modèle de menace

On protège quatre choses : les données des registres, l'identifiant unique de la personne, son
consentement, et la preuve de ce qui a été consulté.

| Menace | Exemple | Réponse principale |
|---|---|---|
| Service trop curieux | Un service demande plus que ce dont sa démarche a besoin. | Habilitation approuvée par l'autorité, accord du citoyen, politique locale du connecteur, niveau `STATUT` qui ne transmet qu'un booléen |
| Passerelle compromise ou administrateur indiscret | Lire les attestations en transit. | Attestation signée par le registre puis chiffrée pour le seul service. La passerelle ne détient pas la clé. |
| Membre usurpé | Une machine se fait passer pour la passerelle ou pour un service. | mTLS avec une autorité interne. L'identité est l'URI du certificat. Chaque route est réservée à un rôle. |
| Rejeu ou altération | Renvoyer une requête interceptée, ou en changer le registre visé. | JWS ES256, 60 s, `jti` à usage unique en base, audience vérifiée |
| Vol de session ou de jeton | Réutiliser un jeton OIDC. | Aucun jeton dans le navigateur. Jeton lié au certificat (`cnf.x5t#S256`), PKCE S256. Trois royaumes étanches. |
| Accord forcé | Un code court photographié, un lien d'accord transmis, une notification envoyée sur l'identifiant d'un autre. | Code de vérification affiché par le service et recopié par le citoyen. Essais comptés. Jamais d'accord d'office sur un identifiant tapé. Une demande qui désigne une personne n'existe pour aucune autre. |
| Énumération de comptes | Deviner des identifiants à l'écran de connexion. | Même page, même adresse et même délai pour un identifiant inconnu. Essais et renvois bornés. Freinage par le proxy. |
| Falsification du journal | Effacer une consultation. | Ajout seul (déclencheurs SQL), chaîne SHA-256, points de contrôle signés |
| Recoupement entre services | Deux services joignent leurs fichiers. | Pseudonyme HMAC propre à chaque service. Sira ne remet l'identifiant unique qu'aux connecteurs, jamais à un service ; un service ne connaît que celui que l'usager lui a saisi pour se désigner, et le service de démonstration ne le conserve pas. |
| Abus d'un instructeur | Préparer et approuver seul une habilitation. | Séparation des rôles (`403 SEPARATION_DES_ROLES`) |

Restent hors du périmètre : la compromission du poste qui héberge toute la pile, la sécurité
physique, les attaques contre Docker lui-même et l'exactitude des registres sources.

## Mesures, emplacement et tests de refus

Les chemins partent de la racine du dépôt ; `.../` abrège `src/main/java/bf/sira/<paquet>/`.

| Mesure | Code | Tests qui la prouvent |
|---|---|---|
| mTLS TLS 1.3, ECDSA P-256. Identité d'un membre = URI SAN du certificat. | `commun/.../identite/SecuriteMembres.java`, `commun/.../mtls/ClientsMtls.java`, `pki/pki.sh` | Passerelle `RefusTest.sansCertificatOuAvecUnCertificatEtrangerLaConnexionEstRefusee`, `lesRoutesDesServicesNAcceptentQueDesServices`. Connecteur `RefusTest.unAutreMembreQueLaPasserelleEstRefuse`, `aucuneAutreRouteNEstOuverte`. `ClientsMtlsTest`, `IdentiteMembreTest`, `IdentiteConnecteurTest` |
| Requête passerelle → connecteur en JWS ES256, 60 s, `jti` unique | `passerelle/serveur/.../collecte/Requetes.java`, `commun/.../jose/Signataire.java`, `VerificateurJws.java`, `consentement-minimisation/connecteur/.../rejeu/RegistreJtiBase.java` | `JoseTest.uneRequeteRejoueeEstRefusee`, `uneRequeteExpireeEstRefusee`, `uneSignatureAltereeEstRefusee`, `uneRequetePourUnAutreRegistreEstRefusee`. Connecteur `RefusTest.uneRequeteModifieeApresSignatureEstRefusee`. `RegistreJtiBaseTest` |
| Attestation : JWS du registre, puis JWE (ECDH-ES+A256KW, A256GCM) pour le service | `commun/.../jose/ScelleurAttestation.java`, `LecteurAttestation.java`, `passerelle/kit/.../ClientSira.java` (`ouvrir`) | `JoseTest.uneAttestationNEstPasLisibleAvecUneAutreCle`, `onNeChiffrePasPourUnAutreMembreQueLeDestinataire`, `uneAttestationNEstLueQueDuRegistreAttendu`. `CachetDuRegistreTest`, `ClientSiraTest` |
| Trois paires de clés par membre (TLS, cachet, chiffrement), qui ne se remplacent pas | `pki/pki.sh`, `commun/.../jose/` | `JoseTest.uneCleDeTransportOuDeChiffrementNeSignePas`, `onNeChiffrePasPourUneCleDeCachetOuDeTransport` |
| Politique locale du connecteur : tout ce qui n'est pas listé est refusé | `consentement-minimisation/connecteur/.../politique/Politique.java`, `registres/connecteurs/*.yaml` | Connecteur `RefusTest.laPolitiqueLocalePrimeSurCeQueDemandeLaPasserelle`, `unJeuIncoherentEstRefuse`. `PolitiqueTest` |
| Le connecteur ne laisse fuiter aucune valeur dans ses journaux ni dans ses erreurs | `consentement-minimisation/connecteur/.../requetes/TraitementRequetes.java`, `consentement-minimisation/connecteur/.../journal/JournalConnecteur.java` | Connecteur `RefusTest.aucuneValeurDuRegistreNiIdentifiantDansLesJournauxApplicatifs`, `uneFicheQueLaRegleNeSaitPasLireEstRefuseeSansCiterLaValeur` |
| Habilitation : un service ne demande que ce pour quoi il est habilité | `passerelle/serveur/.../demandes/Demandes.java`, `passerelle/serveur/.../referentiel/Habilitations.java` | Passerelle `RefusTest.unServiceNeDemandeQueCePourQuoiIlEstHabilite`, `laDemandeDUnAutreServiceNExistePas` |
| Accord : décision unique, dans le délai. Code de vérification exigé. 5 essais, puis 429. | `consentement-minimisation/consentement/.../Accords.java`, `Codes.java` | `RefusTest.uneDecisionNeSePrendQuUneFoisEtDansLeDelai`, `accepterExigeLeCodeAfficheParLeService`, `lesEssaisDeCodeCourtSontComptes`, `lesEssaisRepartisSurPlusieursComptesSontBornes`, `uneNotificationParIdentifiantNEstJamaisAccepteeDOffice`, `uneDemandeDesigneeParLienNExistePasPourUnAutre`, `uneDemandeDesigneeParCodeCourtNExistePasPourUnAutre`, `unIdentifiantDesigneNeRevelePasSiLaPersonneExiste`. `CodesTest`. E2E `accords.spec.ts` |
| Codes tirés de `SecureRandom`, conservés seulement en empreinte HMAC | `consentement-minimisation/consentement/.../Codes.java`, `commun/.../crypto/TempsConstant.java` | `CodesTest` |
| Retour du navigateur seulement vers une adresse déclarée | `passerelle/serveur/.../demandes/` | `RefusTest.leRetourNeVaQueVersUneAdresseDeclaree` |
| Retrait immédiat, avec purge des attestations en dépôt | `consentement-minimisation/consentement/.../Autorisations.java`, `passerelle/serveur/.../collecte/Purge.java` | `RetraitTest.retraitImmediatAvecPurgeDesAttestations`, `unRetraitPendantLaCollecteArreteLesEssaisSuivants`. E2E `parcours-cles.spec.ts` |
| Pseudonymes HMAC-SHA-256 par service | `commun/.../crypto/Pseudonymes.java` | `PseudonymesTest` |
| Journal en ajout seul, chaîné, signé, vérifiable | `passerelle/serveur/src/main/resources/db/migration/V1__schema.sql` (déclencheurs `journal_immuable`), `journal/.../Chaine.java`, `PointsDeControle.java`, `VerificationJournal.java` | `JournalTest.laBaseRefuseDeModifierOuDEffacerUneLigne`, `uneLigneFalsifieeEstDetecteeALaVerification`, `leCitoyenNeLitEtNeSignaleQueSesLignes`. E2E `console.spec.ts` |
| Trois royaumes étanches. Jeton lié au certificat. PKCE. Aucun jeton dans le navigateur. Cookie `HttpOnly`, `SameSite=Lax`, CSRF. | `commun/.../oidc/ConnexionOidc.java`, `RelaisPasserelle.java`, `commun/.../jetons/JetonsRoyaume.java`, `commun/.../config/ReglagesParDefaut.java`, `identite/` | Passerelle `RefusTest.lesDeuxRoyaumesNeSeCroisentPas`, `leJetonDoitEtreLieAuCertificatEtDesignerLeCitoyen`. `AutreRoyaumeTest` (espace citoyen et console). `ConnexionOidcTest` |
| Relais fermé : seules les routes listées passent de l'écran à la passerelle | `espace-usager/serveur/.../relais/RoutesCitoyen.java`, `console/.../relais/RoutesConsole.java`, `Acces.java` | `EspaceCitoyenTest`, `ConsoleTest` |
| Séparation des rôles : l'instructeur prépare, l'autorité décide. L'auditeur lit seulement. | `passerelle/serveur/.../referentiel/Habilitations.java` | `AdministrationTest.uneHabilitationSePrepareSeDecideEtSeSuspendParDesPersonnesDistinctes`, `lesAdressesDeRetourEtDeConnecteurSontDecideesParLAutorite`. E2E `console.spec.ts` |
| Idempotence et validation champ par champ. Corps de requête borné. | `passerelle/serveur/.../demandes/DemandesControleur.java`, `commun/.../erreurs/` | `RefusTest.laCleDIdempotenceEstExigeeEtNeSertQuUneFois`, `uneSaisieInvalideEstRefuseeChampParChamp`. `LimiteCorpsTest` |
| Connexion du citoyen : pas d'énumération. Cinq essais et trois renvois par session, dix codes par heure et par identifiant, débit borné. | `identite/extension/src/bf/sira/identite/IdentifiantAuthenticator.java`, `CodeUsageUnique.java`, `deploiement/proxy/` | `scripts/sira verifier` (sixième essai refusé, même page pour un inconnu, freinage 429). E2E `identite.spec.ts` |
| Conteneurs durcis : lecture seule, `cap_drop: [ALL]`, `no-new-privileges`, mémoire, processus et journaux bornés, réseaux internes séparés | `deploiement/compose.yaml` (`x-durci`), `deploiement/compose.demo.yaml` | `scripts/sira verifier` (une vérification par conteneur, réseaux, en-têtes HTTP) |
| Aucun secret dans le dépôt : tout est tiré au premier démarrage | `deploiement/secrets*.conf`, `deploiement/membres*.conf`, `pki/pki.sh`, `.gitignore` | `scripts/sira verifier` (volumes de l'autre mode refusés, Keycloak refuse les comptes fictifs hors démonstration) |

La cryptographie passe entièrement par des bibliothèques reconnues : JDK, Nimbus JOSE+JWT, Keycloak. Le dépôt ne contient aucun algorithme de sécurité maison.

## Lancer les tests de refus

```sh
./mvnw verify            # tous les tests Java, dont les classes RefusTest (Docker requis pour Testcontainers)
scripts/sira verifier    # sur la pile qui tourne : réseaux, en-têtes, conteneurs, connexion du citoyen
scripts/sira tester      # parcours de bout en bout, dont les refus vus de l'écran
```

## Limites et mise en production

| Sujet | Démonstration | Ce que demande une production |
|---|---|---|
| HTTPS | HTTP sur `127.0.0.1:8600`. `SIRA_HTTPS=true` prépare le passage derrière un frontal TLS, non fourni. | Frontal TLS avec un certificat public, de vrais noms de domaine, HSTS (déjà prévu par le proxy quand `SIRA_HTTPS=true`). |
| Clés et PKI | Autorité créée au premier démarrage sur le poste. Clés dans un volume Docker. Certificats de 90 jours, réémis quand il reste moins de 30 jours. | Clé de l'autorité hors ligne ou en HSM. Clés de cachet et de chiffrement en HSM. Autorité intermédiaire. Émission sur demande signée et approuvée. |
| Révocation | Aucune vérification de révocation : un certificat compromis reste valide jusqu'à son échéance, ou jusqu'à la désactivation du membre dans la console. | CRL ou OCSP vérifiés par chaque membre. |
| Codes de connexion | Envoyés par courriel interne vers un téléphone simulé (Mailpit). | Passerelle SMS d'un opérateur, branchée dans `CodeUsageUnique.transmettre`, avec un engagement de confidentialité. |
| Comptes | Mot de passe commun aux comptes fictifs, lisible par `scripts/sira comptes`. TOTP enrôlé à la première connexion (QR code montré une fois, graine tirée par Keycloak), réinitialisable par `scripts/sira totp-reinitialiser`. | Aucun compte fictif (`scripts/sira demarrer` les refuse). Comptes nominatifs, clé d'accès ou TOTP de 160 bits. |
| Hébergement | Un seul poste, Docker Compose. | Hébergement souverain, sur le territoire, avec sauvegardes chiffrées, supervision, plan de reprise, et une passerelle redondée (la file d'attente le permet déjà avec `SKIP LOCKED`). |
| Transport | Liaison directe mTLS. | Liaison directe mTLS ; une autre route (X-Road par exemple) se brancherait derrière la même interface. |
| Registres | Simulés, données fictives. | Connecteur installé chez chaque administration, avec sa propre politique locale. |

### Cadre légal

Une mise en service réelle relèverait de la loi n° 001-2021/AN du 30 mars 2021 portant protection des personnes à l'égard du traitement des données à caractère personnel. Elle demanderait notamment :

- l'avis de l'autorité de contrôle sur l'acte réglementaire qui créerait le traitement (article 30). Cette autorité est la CIL, en cours de fusion au sein de l'ARCOD ;
- son autorisation pour l'interconnexion de fichiers et pour l'usage d'un numéro national d'identification (article 31). Sira relève de ces deux cas ;
- une interconnexion qui poursuit des objectifs légaux, sécurisée et pertinente (article 41). C'est ce que les mesures ci-dessus visent, sans s'y substituer ;
- dans chaque administration, un responsable de traitement désigné et des durées de conservation fixées ;
- l'information des personnes et l'exercice de leurs droits. Le journal consultable et le retrait d'accès en sont une base technique, pas une conformité en soi.

Aucune de ces démarches n'a été engagée pour ce prototype. Sira n'est pas déclaré conforme.
