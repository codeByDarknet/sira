# Serveurs de l'espace citoyen et de la console

Deux serveurs Spring distincts, volontairement minces : `espace-usager/serveur` et `console`.
Chacun tient la session de **sa** population et relaie `/api/**` vers la passerelle. Aucun ne porte
de règle métier ni de base de données : tout ce qui décide est dans la passerelle.

| | Espace citoyen | Console |
|---|---|---|
| Module | `espace-usager/serveur` | `console` |
| Royaume accepté (le seul) | `sira-citoyens` | `sira-socle` |
| Client OIDC | `espace-citoyen` | `console` |
| Identité mTLS | `urn:sira:socle:espace-citoyen` | `urn:sira:socle:console` |
| `/api/**` relayé vers | `/v1/citoyen/**` | `/v1/admin/**` |
| Cookie de session | `SIRA_CITOYEN` | `SIRA_CONSOLE` |
| Contrôle des rôles | sans objet | lecture, écriture, décision, traitement |

## Ce que fait un serveur

1. **Session.** Connexion OIDC par code et PKCE, client authentifié par son certificat
   (`tls_client_auth`). Les jetons restent côté serveur ; le navigateur ne reçoit qu'un cookie
   `HttpOnly`, `SameSite=Lax`. Routes fournies par `bf.sira.commun.oidc` : `GET /api/session`,
   `GET /api/auth/connexion/sira`, `GET /api/auth/retour`, `POST /api/auth/deconnexion`.
2. **Liste blanche.** Une requête n'est relayée que si sa méthode et son chemin figurent dans
   `RoutesCitoyen` ou `RoutesConsole`. Les parties variables n'ont qu'une forme (UUID, numéro, code
   métier) et le chemin est comparé brut, sans décodage : un caractère encodé, un `..` ou une barre
   en trop ne correspondent à rien et répondent `404`, sans atteindre la passerelle.
3. **Rôles (console).** Chaque route porte l'accès qu'elle exige (`Acces`) : lecture pour
   `instructeur`, `autorite`, `auditeur` ; écriture pour `instructeur` ; décision (approbation,
   rejet, suspension) pour `autorite` ; traitement d'un signalement pour `instructeur` ou
   `autorite`. Un rôle insuffisant répond `403`. C'est un premier contrôle : la passerelle refait le
   sien, qui seul fait foi (séparation des rôles comprise).
4. **Relais.** L'appel part en mTLS avec le jeton d'accès de la session, renouvelé s'il est échu,
   vers un serveur qui doit présenter l'identité `urn:sira:socle:passerelle` (Keycloak :
   `urn:sira:socle:identite`) ; un autre membre est refusé avant tout envoi du jeton.
   Rien de ce qu'envoie le navigateur ne part tel quel : aucun en-tête, aucun cookie. Seuls sont
   repris le corps (JSON uniquement, borné par `sira.http.taille-corps-max`) et les paramètres de
   requête (12 au plus, noms en minuscules, 200 caractères par valeur, réencodés).
5. **Réponse.** Statut et corps de la passerelle sont rendus tels quels, erreurs `problem+json`
   comprises. Une réponse qui n'est pas du JSON, une redirection ou un corps de plus de 1 Mo
   répondent `502`. Passerelle injoignable : `503`.

Un `401` de la passerelle, ou un jeton qui ne se renouvelle plus, ferme la session : l'interface
renvoie alors vers la connexion.

## Refus, et le test qui le prouve

Les tests sont dans `EspaceCitoyenTest` et `ConsoleTest`, face à un fournisseur d'identité et une
passerelle simulés, tous deux en mTLS.

| Situation | Réponse | Test |
|---|---|---|
| Pas de session | `401` JSON | `sansSessionToutRepond401EnJson` |
| Jeton présenté par le navigateur | `401` | `unJetonPresenteParLeNavigateurNOuvreRien` |
| Jeton émis par un autre royaume | pas de session | `AutreRoyaumeTest` |
| Écriture sans `X-XSRF-TOKEN` | `403` | `uneEcritureSansJetonCsrfEstRefusee` |
| Route ou méthode hors liste, chemin difforme | `404` | `uneRouteHorsListeRepond404SansAtteindreLaPasserelle` |
| Rôle insuffisant (console) | `403` | `seulLInstructeurEcrit`, `seuleLAutoriteDecide`, `lAuditeurNeTraitePasUnSignalement`, `sansRoleDeLaConsoleRienNEstRelaye` |
| Corps trop gros, format autre que JSON, paramètre hors bornes | `413`, `415`, `400` | `lesEntreesHorsBornesSontRefusees` |
| Réponse inattendue de la passerelle | `502` | `uneReponseInattendueDeLaPasserelleNEstPasTransmise` |
| Jeton refusé par la passerelle | `401`, session fermée | `unJetonRefuseParLaPasserelleFermeLaSession` |

`DemarrageTest` lance le serveur avec sa configuration livrée : port, cookie, sonde de santé,
départ vers le bon royaume.

## Configuration

Le royaume et le client sont écrits dans `application.yaml` ; seules les adresses se règlent.

| Variable | Défaut (citoyen / console) |
|---|---|
| `SIRA_URL_PUBLIQUE` | `http://citoyen.sira.localhost:8600` / `http://console.sira.localhost:8600` |
| `SIRA_IDENTITE_URL_PUBLIQUE` | `http://identite.sira.localhost:8600` |
| `SIRA_IDENTITE_URL_INTERNE` | `https://keycloak:8443` |
| `SIRA_PASSERELLE_URL` | `https://passerelle:8443` |

Changer `SIRA_PORT` impose de régler les deux premières : elles font partie de l'émetteur des jetons
et de l'adresse de retour.

Ports : `8080` (HTTP, derrière le nginx de l'interface), `8081` sur `127.0.0.1` (sonde de santé).
Fichiers lus : `/run/sira/pki/ca.crt`, `tls.crt`, `tls.key`. Ni base de données, ni secret.
