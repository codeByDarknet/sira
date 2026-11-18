# Tests de bout en bout

Les tests unitaires montrent que chaque module fait ce qu'il doit. Ceux-ci montrent que l'ensemble tient debout : ils ouvrent un vrai navigateur (Playwright 1.64.1) sur la pile lancée par `scripts/sira` et refont ce que ferait une personne. Aïcha saisit son identifiant dans le formulaire du concours, donne son accord dans Sira, retrouve le formulaire prérempli et verrouillé, et le service ne reçoit que « majeur : oui », son nom et ses prénoms ; un citoyen retire un accès et la démarche suivante le lui redemande ; le casier tombe en panne et le dossier se complète quand il revient ; une habilitation préparée par l'instructeur n'est approuvée que par un autre compte.

## Lancer

Depuis la racine du dépôt, avec Node.js 24 :

```sh
scripts/sira demo                # la pile reste en marche
scripts/sira tester              # installe au besoin, puis lance les scénarios
scripts/sira tester --grep "code court"  # les options suivent celles de Playwright
```

`scripts/sira tester` choisit les scénarios selon la pile qui tourne : ceux de la démonstration (`ossature` et `parcours`) si elle est lancée, ceux du socle seul (`socle`) après `scripts/sira demarrer`. À la main :

```sh
cd tests/e2e
npm ci
npx playwright install chromium  # une seule fois
npm test                         # ossature et parcours
npm run ossature                 # identité, messagerie, téléphone simulé
npm run parcours                 # parcours complets, pile de démonstration
npm run socle                    # socle seul : aucune adresse de démonstration ne répond
npm run rapport                  # rapport HTML du dernier passage
```

Deux variables changent les adresses visées : `SIRA_PORT` (par défaut `8600`) et `SIRA_TELEPHONE`, si le téléphone simulé est servi ailleurs que sur `http://telephone.sira.localhost:$SIRA_PORT`.

Aucun secret n'est écrit dans le dépôt. Le mot de passe des comptes de démonstration est lu par `scripts/sira comptes` au moment de s'en servir. Pour la console, les tests font comme une vraie personne : à la première connexion, ils lisent la clé affichée sous le QR code et la gardent dans `tests/e2e/.totp/` (ignoré par Git) ; sans clé gardée, ou si elle ne marche plus, ils lancent `scripts/sira totp-reinitialiser`, ce qui efface aussi une application ajoutée à la main sur un téléphone. Les codes de connexion des citoyens sont lus dans la messagerie de démonstration, par le relais en lecture seule du téléphone simulé.

## Ce qui est joué

`outils/` regroupe ce que les scénarios partagent : adresses, comptes, connexions (citoyen, administrateur, agent), lecture de la messagerie, gestes sur le portail d'un service et sur le back-office d'un registre.

`scenarios/ossature/` vérifie ce qui ne dépend que de Keycloak, de la messagerie, du proxy et du téléphone. `scenarios/socle/` vérifie que, sur le socle seul, ni le téléphone, ni les services, ni les registres de démonstration ne répondent.

`scenarios/parcours/` porte les parcours eux-mêmes. `parcours-cles.spec.ts` suit les démarches du concours et de la bourse : minimisation, historique et retrait d'accès, attestation PDF reçue, puis panne et reprise du casier. `demarches.spec.ts` suit l'immatriculation d'un commerçant, dont les trois réponses viennent de trois registres. `formulaire.spec.ts` suit le formulaire de la démarche : l'identifiant d'un autre citoyen saisi au service mène à une demande introuvable, par redirection comme par code court ; le formulaire revient prérempli, ce qui vient des registres est verrouillé à l'écran et refusé par le serveur ; un fichier qui n'est pas un PDF est refusé ; un CV déposé se prévisualise et se télécharge ; après le dépôt, rien ne change plus ; l'agent voit les deux sections du dossier. `accords.spec.ts` couvre les autres façons de donner son accord (code court, demande retrouvée par identifiant unique) et le refus. `console.spec.ts` crée une question, l'essaie, fait approuver une habilitation par un second compte et vérifie l'intégrité du journal. `populations.spec.ts` vérifie qu'un citoyen n'ouvre pas la console et qu'un agent n'ouvre pas l'espace citoyen.

`scenarios/etranger/` est lancé à part, par `scripts/sira etranger`, qui démarre d'abord un registre volontairement différent et son connecteur ; le scénario le raccorde depuis la console et va jusqu'à une réponse reçue par le service des concours.

Chaque règle de sécurité vérifiée ici l'est par son refus : code faux, identifiant inconnu, identifiant d'un autre citoyen, information d'un registre modifiée, fichier qui n'est pas un PDF, pièce lue hors de sa session, adresse de retour étrangère, écriture dans la messagerie, demande d'un autre citoyen, approbation par l'instructeur, écriture par l'auditeur, session d'une autre population.

## À savoir

Les scénarios s'exécutent un par un, parce qu'ils partagent les comptes et l'état des registres ; celui de la panne remet toujours le casier en marche normale, même s'il échoue. Le portail d'un service garde un seul dossier par citoyen : une fois déposé, le formulaire de ce citoyen ne s'ouvre plus en écriture. Chaque parcours emploie donc un citoyen différent, mais une pile déjà utilisée garde les accords des passages précédents, et un citoyen fictif ne reçoit que dix codes de connexion par heure : pour repartir de zéro, `scripts/sira reinitialiser` puis `scripts/sira demo`.

Les sélecteurs suivent les libellés des écrans, par leur rôle et leur texte accessibles. Si vous renommez un bouton, cherchez-le ici ; ceux du portail de service sont regroupés en tête de `outils/service.ts`.
