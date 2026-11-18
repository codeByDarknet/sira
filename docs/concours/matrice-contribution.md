# Matrice de contribution

Équipe « codeforchange », IBAM Ouagadougou. Ce document a deux parties : qui a fait quoi, puis les briques libres que Sira réutilise et leurs licences.

## 1. Contribution des membres

L'équipe remplit chaque case avec ce que le membre a fait. Laissez une case vide si le membre n'a pas travaillé sur cette partie.

| Partie | Landry Ezéïkiel OUARMA | Abdoul Razack OUEDRAOGO | Laodou Othniela KY |
|---|---|---|---|
| Registres (`registres/`) | Registres simulés, connecteurs et jeux de données | Revue du contrat OpenAPI et des raccordements | Console d'administration des registres (`interfaces/demo/`) |
| Passerelle d'échange (`passerelle/`) | Essais de transport et résilience des appels | Cœur de la passerelle : collecte, journal, pseudonymisation | Écran de consentement et suivi usager |
| Consentement et minimisation (`consentement-minimisation/`) | Scénarios de refus et d'accès | Connecteur, politique locale et réponses signées | Écran d'accord et retrait d'accès |
| Journal de traçabilité (`journal/`) | Journal chaîné et relance après panne | Vérification d'intégrité des chaînes | Historique lisible dans l'espace usager |
| Espace usager (`espace-usager/`, `interfaces/socle/projects/espace-citoyen`) | Tests de bout en bout du parcours | API des demandes et des accès | Écrans de l'espace citoyen |
| Guichet agent, bonus (`guichet-agent/`, `interfaces/demo/`) | Portails de démonstration et scripts de lancement | Service de démonstration et attestations | Formulaires, pièces et tableaux de bord |
| Résilience, bonus (`resilience/`) | Disjoncteur, nouveaux essais et file d'attente | Intégration avec la passerelle | Messages d'indisponibilité |
| Identité et connexion (`identite/`) | Certificats et secrets de démarrage | Realms, OIDC et code à usage unique | Thème de connexion et écrans TOTP |
| Console d'administration (`console/`) | Déploiement de la console | API de la console | Écrans et assistants de la console |
| Certificats et sécurité (`pki/`, `commun/`) | Autorité de certification et génération des secrets | mTLS, JOSE, agents et erreurs RFC 9457 | Écrans de connexion et identité visuelle |
| Déploiement (`deploiement/`, `scripts/`) | Compose, proxy et commande `scripts/sira demo` | Réglages des services et du journal | Documentation de lancement |
| Tests de bout en bout (`tests/e2e/`) | Jeu de scénarios Playwright | Tests unitaires et contrats OpenAPI | Scénarios d'accord et de démonstration |
| Documentation et rapport (`docs/`) | Matrice de contribution et licences | Contrat d'architecture, API et sécurité | Guides d'écrans et présentation |
| Vidéo et présentation | Captation et montage de la démonstration | Revue technique du scénario | Plan de la vidéo et supports |

## 2. Composants réutilisés

Sira est publié sous licence EUPL-1.2. Les versions sont celles résolues par le build. Les licences ont été lues dans les fichiers des composants eux-mêmes : déclaration `<licenses>` des POM Maven (en remontant aux POM parents), champ `license` des `package.json` installés, étiquettes et fichiers de licence des images Docker. « À vérifier » signifie que la licence n'a pas pu être lue dans le composant.

L'avis de compatibilité est une analyse indicative de l'équipe, pas un avis juridique. Il repose sur deux règles :

- un composant sous licence permissive (Apache-2.0, MIT, BSD, 0BSD) peut être livré avec du code EUPL-1.2 ; il garde sa propre licence et son avis de copyright ;
- un composant sous licence à copyleft faible (LGPL, MPL-2.0, EPL-2.0) est utilisé tel quel, comme bibliothèque séparée, sans modification ; ses modifications éventuelles resteraient sous sa licence. L'annexe de l'EUPL-1.2 cite d'ailleurs la LGPL v2.1, la MPL v2 et l'EPL v1.0 parmi ses licences compatibles.

### 2.1 Bibliothèques Java (exécution)

| Composant | Version | Rôle dans Sira | Licence | Compatibilité avec l'EUPL-1.2 |
|---|---|---|---|---|
| Spring Boot | 4.1.2 | Cadre des serveurs Java | Apache-2.0 | Oui |
| Spring Framework | 7.0.10 | Web, transactions, accès aux données | Apache-2.0 | Oui |
| Spring Security | 7.1.2 | mTLS, OIDC, contrôle des rôles | Apache-2.0 | Oui |
| Apache Tomcat (embarqué) | 11.0.27 | Serveur HTTP | Apache-2.0 | Oui |
| Nimbus JOSE+JWT | 10.10 | JWS, JWE, JWK | Apache-2.0 | Oui |
| Nimbus OAuth 2.0 SDK with OIDC (`oauth2-oidc-sdk`) | 11.38.2 | Client OpenID Connect | Apache-2.0 | Oui |
| json-smart | 2.6.0 | Analyse JSON pour Nimbus | Apache-2.0 | Oui |
| Resilience4j (core, circuitbreaker, retry, bulkhead) | 2.4.0 | Disjoncteur, nouveaux essais, cloison | Apache-2.0 | Oui |
| Flyway (core, database-postgresql) | 12.4.0 | Migrations de schéma | Apache-2.0 | Oui |
| Pilote JDBC PostgreSQL | 42.7.14 | Accès à la base | BSD-2-Clause | Oui |
| HikariCP | 7.0.2 | Pool de connexions | Apache-2.0 | Oui |
| Jackson (databind 3.1.7, annotations 2.21) | 3.1.7 / 2.21 | JSON | Apache-2.0 | Oui |
| SnakeYAML | 2.6 | Lecture des politiques et de l'amorçage YAML | Apache-2.0 | Oui |
| Hibernate Validator | 9.1.5.Final | Validation des entrées | Apache-2.0 | Oui |
| Micrometer | 1.17.2 | Métriques | Apache-2.0 | Oui |
| SLF4J | 2.0.20 | Interface de journalisation | MIT | Oui |
| Logback | 1.5.38 | Journalisation technique | EPL-2.0 ou LGPL-2.1-only, au choix | Oui, utilisé sans modification |
| OpenPDF | 2.0.3 | Attestations PDF des registres simulés (`registres/` seulement) | LGPL-2.1 ou MPL-2.0, au choix | Oui, utilisé sans modification |

### 2.2 Bibliothèques Java (tests et build)

Ces composants ne sont pas livrés dans les images.

| Composant | Version | Rôle dans Sira | Licence | Compatibilité avec l'EUPL-1.2 |
|---|---|---|---|---|
| JUnit Jupiter | 6.0.3 | Tests | EPL-2.0 | Oui, non livré |
| AssertJ | 3.27.7 | Assertions des tests | Apache-2.0 | Oui |
| Mockito | 5.23.0 | Doublures de test | MIT | Oui |
| Testcontainers | 2.0.5 | PostgreSQL réel pendant les tests | MIT | Oui |
| Bouncy Castle (`bcpkix`) | 1.87 | Certificats de test | Bouncy Castle Licence (texte de type MIT) | Oui, non livré |
| Spotless (plugin Maven) | 3.11.0 | Contrôle de la mise en forme | Apache-2.0 | Oui, non livré |
| google-java-format | 2.0.0 | Style du code Java | Apache-2.0 | Oui, non livré |

### 2.3 Bibliothèques des interfaces (exécution, dans les pages servies)

| Composant | Version | Rôle dans Sira | Licence | Compatibilité avec l'EUPL-1.2 |
|---|---|---|---|---|
| Angular (common, compiler, core, forms, platform-browser, router) | 21.2.25 | Écrans de l'espace citoyen, de la console et de la démonstration | MIT | Oui |
| RxJS | 7.8.2 | Flux asynchrones d'Angular | Apache-2.0 | Oui |
| tslib | 2.8.1 | Fonctions d'appoint TypeScript | 0BSD | Oui |
| jsQR | 1.4.0 | Lecture du QR code de vérification (socle) | Apache-2.0 | Oui |
| PrimeNG | 21.1.10 | Composants d'interface (boutons, champs, messages, étiquettes, fenêtres) | MIT | Oui |
| @primeuix/themes (préréglage Aura) | 2.0.3 | Thème PrimeNG, recoloré en bleu Sira | MIT | Oui |
| @angular/cdk | 21.2.14 | Dépendance de PrimeNG | MIT | Oui |
| pdf.js (`pdfjs-dist`) | 5.4.394 | Aperçu des pièces PDF dans le portail des démarches (démonstration) | Apache-2.0 | Oui |
| Police Nunito, auto-hébergée (`interfaces/socle/projects/ui/assets/fonts/`, `identite/theme/sira/login/resources/fonts/`) | 5.3.0 (Fontsource) | Police de toute l'interface | SIL Open Font License 1.1 (`LICENCE-Nunito-OFL.txt`) | Oui : la police reste sous OFL, livrée avec sa licence |
| Photo de la page de connexion (`identite/theme/sira/login/resources/img/connexion-equipe.jpg`) | Pexels 1181406 | Fond du panneau gauche de la page de connexion | Licence Pexels (usage gratuit, sans attribution obligatoire) | Oui : image utilisée telle quelle, non revendue seule |
| qrcode-generator | 2.0.4 | Affichage du QR code (démonstration) | MIT | Oui |
| Icônes Lucide (copie dans `interfaces/socle/projects/ui/assets/icones.svg`) | 1.59.0 | Icônes des écrans | ISC ; icônes issues de Feather sous MIT (`LICENCE-Lucide-ISC.txt`) | Oui |
| Police Public Sans, auto-hébergée (`interfaces/socle/projects/ui/assets/fonts/`) | Non indiquée dans les fichiers | Police des écrans | SIL Open Font License 1.1 (`LICENCE-PublicSans-OFL.txt`) | Oui : la police reste sous OFL, livrée avec sa licence |

### 2.4 Outils des interfaces et des tests de bout en bout (non livrés)

| Composant | Version | Rôle dans Sira | Licence | Compatibilité avec l'EUPL-1.2 |
|---|---|---|---|---|
| Angular CLI, @angular/build | 21.2.29 | Construction des écrans | MIT | Oui |
| @angular/compiler-cli | 21.2.25 | Compilation des gabarits | MIT | Oui |
| TypeScript | 5.9.3 | Langage des écrans et des tests | Apache-2.0 | Oui |
| Tailwind CSS, @tailwindcss/postcss | 4.3.5 | Utilitaires CSS des écrans | MIT | Oui |
| tailwindcss-primeui | 0.6.1 | Utilitaires Tailwind branchés sur les jetons PrimeNG | MIT | Oui |
| PostCSS | 8.5.33 | Passage de Tailwind dans la construction Angular | MIT | Oui |
| ESLint, @eslint/js | 10.13.0 / 10.0.1 | Analyse statique | MIT | Oui |
| angular-eslint | 21.4.0 | Règles Angular | MIT | Oui |
| typescript-eslint | 8.72.2 | Règles TypeScript | MIT | Oui |
| eslint-config-prettier | 10.1.8 | Accord ESLint et Prettier | MIT | Oui |
| Prettier | 3.9.6 | Mise en forme | MIT | Oui |
| Vitest | 4.1.12 | Tests unitaires des écrans | MIT | Oui |
| jsdom | 28.1.0 | Navigateur simulé des tests unitaires | MIT | Oui |
| Playwright (`@playwright/test`) | 1.64.1 | Tests de bout en bout | Apache-2.0 | Oui |
| @types/node | 24.10.1 | Types Node.js des tests | MIT | Oui |

### 2.5 Images et logiciels de la pile

Ces logiciels tournent dans leurs propres conteneurs, à côté de Sira : ce sont des programmes séparés, pas des œuvres dérivées.

| Composant | Version | Rôle dans Sira | Licence | Compatibilité avec l'EUPL-1.2 |
|---|---|---|---|---|
| Keycloak | 26.8.1 | Fournisseur d'identité, trois royaumes | Apache-2.0 | Oui |
| PostgreSQL (`postgres:17.11-alpine`) | 17.11 | Bases de données | À vérifier (aucune licence lisible dans l'image ; PostgreSQL est publié sous la PostgreSQL License, de type BSD) | Oui, logiciel séparé |
| nginx (`nginxinc/nginx-unprivileged:1.31.7-alpine`) | 1.31.7 | Proxy d'entrée, service des écrans | Licence BSD à 2 clauses (paquet nginx) ; fichiers de l'image sous Apache-2.0 | Oui, logiciel séparé |
| Mailpit | v1.27.11 | Téléphone simulé (réception des codes) | MIT | Oui, démonstration seulement |
| Eclipse Temurin (JDK de build, JRE d'exécution) | 21.0.13+9 | Machine Java | GPL-2.0 avec Classpath Exception | Oui : l'exception permet d'exécuter un programme sous une autre licence |
| Alpine Linux (base de `pki/`) | 3.24.3 | Image de génération des certificats | À vérifier (aucune licence globale lue dans l'image ; chaque paquet a la sienne) | Oui, logiciel séparé |
| OpenSSL (dans `pki/`) | 3.5.9 | Émission des certificats | Apache-2.0 | Oui |
| Node.js (`node:24.22.0-alpine`) | 24.22.0 | Construction des écrans, étape de build seulement | À vérifier | Oui, non livré |

### 2.6 Standards

Les standards suivis (TLS 1.3, JOSE, OpenID Connect, OAuth 2.0 mTLS) sont des spécifications publiques, mises en œuvre par les composants ci-dessus.
