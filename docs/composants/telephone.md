# Téléphone simulé

Écran de démonstration, servi sur `http://telephone.sira.localhost:<port>` par `scripts/sira demo`
seulement : sur le socle seul (`scripts/sira demarrer`), ni l'écran ni Mailpit n'existent et ce nom
répond 404. Il remplace le téléphone des citoyens fictifs : il affiche, comme une messagerie SMS, les codes de connexion à
usage unique que le fournisseur d'identité leur envoie.

Dans la réalité, ce code arrive par SMS. Ici, Keycloak l'envoie par courriel à l'adresse
`<identifiant unique>@telephone.sira.example`, la messagerie de démonstration (Mailpit) le retient,
et cet écran le lit. Rien ne sort de la machine.

## Ce que fait l'écran

- Choix du téléphone par le nom du citoyen (les six citoyens de
  `identite/demo/sira-citoyens-users-0.json`).
- Dernier code reçu mis en évidence, avec un bouton pour le copier.
- Les 10 derniers messages, relus toutes les 3 secondes tant que l'onglet est visible.
- États vide, chargement et erreur ; bandeau de démonstration commun aux écrans simulés.

Code : `interfaces/demo/projects/telephone` (Angular, composants `@sira/ui` seuls). L'écran n'a ni
session ni serveur d'application : il ne sait faire qu'une chose, une recherche en lecture.

## Lecture de la messagerie : un relais, en lecture seule

L'écran appelle `GET /messagerie/search?query=to:"<adresse>"&limit=10`, même origine. Le
proxy d'entrée relaie cette seule route vers la recherche de Mailpit et refuse tout le reste :
l'interface de Mailpit et son API d'écriture (suppression, envoi, relais) ne sont pas exposées.

Bloc de `deploiement/proxy/demo.conf.template`, monté par `compose.demo.yaml` (extrait) :

```nginx
server {
  listen 8080;
  server_name telephone.sira.localhost;

  # Variables : le proxy démarre même si un amont n'est pas encore là.
  set $messagerie mailpit:8025;
  set $ecran web-telephone:8080;

  location = /messagerie/search {
    limit_except GET { deny all; }
    proxy_pass http://$messagerie/api/v1/search$is_args$args;
    proxy_http_version 1.1;
    proxy_set_header Host $messagerie;
    proxy_hide_header Access-Control-Allow-Origin;
    proxy_hide_header Access-Control-Allow-Methods;
    proxy_hide_header Access-Control-Allow-Headers;
    add_header Cache-Control "no-store" always;
    add_header X-Content-Type-Options "nosniff" always;
  }
  location /messagerie/ { return 404; }
  location /api/ { return 404; }
  location / { proxy_pass http://$ecran; }
}
```

Le bloc reprend le `resolver` et la page d'indisponibilité de `sira.conf.template`. Une autre
méthode que `GET` répond 403 ; l'interface de Mailpit, son API et son WebSocket répondent 404.

Service dans `deploiement/compose.demo.yaml` :

| Élément | Valeur |
|---|---|
| Image | `deploiement/docker/web.Dockerfile`, `ESPACE=interfaces/demo`, `APPLICATION=telephone` |
| Nom | `web-telephone`, port interne 8080, aucun port publié |
| Variables | `API_AMONT` vide : l'image ne relaie rien elle-même |
| Réseau | `entree` seul ; c'est le proxy, qui ne rejoint `messagerie` qu'en démonstration, qui joint Mailpit |
| Dépendances | aucune au démarrage ; sans Mailpit, l'écran affiche son état d'erreur |

## Limites

- Écran de démonstration, sans authentification : quiconque atteint la machine lit les codes des
  citoyens fictifs. Il n'existe pas en production, où le code part par SMS.
- La recherche de Mailpit n'est pas restreinte aux six adresses : la messagerie ne contient que
  des messages de démonstration.
- La liste des citoyens est recopiée dans `messages/citoyens.ts` ; le test
  `tests/e2e/scenarios/ossature/telephone.spec.ts` vérifie qu'elle suit le fichier d'identité.

## Vérifier

```sh
cd interfaces/demo && npx ng build telephone && npx eslint projects/telephone
scripts/sira verifier    # refus du relais : POST 403, API et WebSocket de Mailpit fermés
cd tests/e2e && npm run ossature
```
