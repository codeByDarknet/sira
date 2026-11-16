# Sécurité

Sira transporte des informations personnelles entre administrations. Une faille peut donc toucher des personnes, pas seulement du code, et nous prenons chaque signalement au sérieux.

## Ce qui est suivi

Sira est un prototype. Seule la branche principale reçoit des corrections. La pile de démonstration (`scripts/sira demo`) contient des comptes à mot de passe connu et des codes de connexion lisibles par tous sur le téléphone simulé : elle ne doit jamais être exposée au réseau, et ce qu'elle laisse voir à ce titre n'est pas une faille.

## Signaler une vulnérabilité

N'ouvrez pas de ticket public. Utilisez le signalement privé de vulnérabilités de la plateforme qui héberge le dépôt, ou écrivez en privé aux mainteneurs de l'équipe codeforchange.

Pour que nous puissions reproduire le problème, indiquez le composant concerné, la version ou le commit, les étapes suivies et ce que vous avez observé. Travaillez uniquement avec les données fictives de la démonstration, et ne tentez rien contre une installation qui ne vous appartient pas.

Nous accusons réception, puis nous vous tenons au courant de l'analyse et de la correction.

## Ce qui est déjà connu

Le modèle de menace, chaque mesure avec le test qui la vérifie, et les limites assumées sont décrits dans [docs/securite.md](docs/securite.md). Une limite qui y figure déjà, par exemple le HTTP en local, l'absence de vérification de révocation des certificats ou l'absence de vrai SMS, n'est pas une vulnérabilité au sens de ce document. Nous sommes en revanche preneurs de tout moyen de contourner une mesure qui y est décrite.
