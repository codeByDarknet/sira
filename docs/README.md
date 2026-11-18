# Documentation de Sira

Une candidate s'inscrit à un concours de la fonction publique. Aujourd'hui, on lui demande un
extrait d'acte de naissance, une copie de diplôme, parfois un extrait de casier : autant de
déplacements pour remettre à une administration des informations qu'une autre administration
détient déjà. Avec Sira, le service du concours pose la question au registre concerné, la
candidate donne son accord depuis son espace, et le service reçoit seulement ce qu'il lui faut :
« majeure : oui », sans la date de naissance.

Sira est le socle qui rend cet échange possible entre administrations : une passerelle, un
connecteur posé devant chaque registre, l'accord de la personne, et un journal qu'elle peut
consulter. Le [README du dépôt](../README.md) présente le projet et le lancement ; cette page
vous oriente selon ce que vous venez faire.

## Essayer

La démonstration se lance avec `scripts/sira demo` et tourne entièrement sur votre poste, avec
des personnes et des services fictifs. Le [README du dépôt](../README.md) donne les adresses et
les comptes. Pour savoir ce qui est simulé et comment le provoquer (pannes de registre, données
de test) : [services simulés](composants/demo-services.md) et
[registres simulés](composants/demo-registres.md).

## Comprendre

Commencez par [suivre une demande](comprendre.md) du service jusqu'au registre, fichier par
fichier ; la page se termine par une foire aux questions. Viennent ensuite :

- [la sécurité](securite.md) : ce contre quoi Sira protège, comment, et ce qui reste à faire
  avant une mise en production ;
- [le contrat](architecture/CONTRAT.md) : les règles que tous les modules respectent
  (identités, cryptographie, formats, journal) ;
- [les API](architecture/API.md) : les formes de l'espace citoyen et de la console, avec un
  renvoi vers la spécification OpenAPI de chaque module.

## Raccorder un registre

[Brancher un registre](brancher-un-registre.md) explique comment poser un connecteur devant un
registre existant sans le modifier : deux fichiers YAML, un certificat, puis la déclaration
dans la console. Le fonctionnement interne du connecteur est décrit dans
[composants/connecteur.md](composants/connecteur.md).

## Contribuer

Le dépôt est rangé en un dossier par module ; chacun a son propre `README.md`
([passerelle](../passerelle/README.md), [consentement-minimisation](../consentement-minimisation/README.md),
[journal](../journal/README.md), [resilience](../resilience/README.md),
[registres](../registres/README.md), [espace-usager](../espace-usager/README.md),
[guichet-agent](../guichet-agent/README.md)). Pour écrire du code :

- [backend.md](developpement/backend.md) : briques Java communes, kit `ClientSira`,
  conventions et tests ;
- [interfaces.md](developpement/interfaces.md) : applications Angular et bibliothèque `@sira/ui` ;
- [les tests de bout en bout](../tests/e2e/README.md) ;
- [CONTRIBUTING.md](../CONTRIBUTING.md) pour la manière de proposer une modification.

Les pages de [composants/](composants/) décrivent chaque serveur en détail :
[passerelle](composants/passerelle.md), [connecteur](composants/connecteur.md),
[serveurs d'application](composants/serveurs-applications.md),
[téléphone simulé](composants/telephone.md).

## Exploiter

[infrastructure.md](developpement/infrastructure.md) décrit `scripts/sira`, les fichiers
Docker Compose, les réseaux, la PKI et les limites de la configuration fournie. Les points à
reprendre avant une mise en production sont rassemblés à la fin de [securite.md](securite.md).
Pour signaler une faille : [SECURITY.md](../SECURITY.md).

Sira est publié sous licence [EUPL-1.2](../LICENSE). Les documents rédigés pour le concours
où le projet a été présenté sont rangés à part, dans [concours/](concours/rapport-technique.md) :
[le rapport technique](concours/rapport-technique.md) et la
[matrice de contribution](concours/matrice-contribution.md).
