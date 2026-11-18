# Image commune à toutes les interfaces web de Sira : une application Angular servie par nginx.
# Contexte de build : la racine du dépôt. Exemples :
#   docker build -f deploiement/docker/web.Dockerfile \
#     --build-arg ESPACE=interfaces/socle --build-arg APPLICATION=espace-citoyen -t sira/web-citoyen .
#   docker build -f deploiement/docker/web.Dockerfile \
#     --build-arg ESPACE=interfaces/demo --build-arg APPLICATION=portail-service -t sira/web-portail .

# Versions exactes, épinglées par empreinte : une reconstruction donne la même base.
ARG NODE_IMAGE=node:24.22.0-alpine@sha256:44fce696f38ba8c987c4db89954051000bbd05ffd8d8659ba15ed4a8602e937f
ARG NGINX_IMAGE=nginxinc/nginx-unprivileged:1.31.7-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e

# --- Dépendances puis sources (sans exécuter les scripts d'installation des paquets). Tout ce qui précède APPLICATION est commun aux applications
#     d'un même espace de travail : ces couches ne sont construites qu'une fois.
FROM ${NODE_IMAGE} AS build
ARG ESPACE
WORKDIR /src/${ESPACE}
COPY ${ESPACE}/package.json ${ESPACE}/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund --ignore-scripts
# Les bibliothèques du socle servent aux deux espaces de travail.
COPY interfaces/socle/projects/ui /src/interfaces/socle/projects/ui
COPY interfaces/socle/projects/coquille /src/interfaces/socle/projects/coquille
COPY ${ESPACE} ./
# L'espace de démonstration prend sa copie de travail des bibliothèques ; le socle n'a rien à faire.
RUN npm run --if-present bibliotheques
ARG APPLICATION
# Le build échoue si un budget de taille est dépassé. Le lint et les tests tournent hors de l'image.
RUN npx ng build ${APPLICATION}

# --- Exécution : nginx sans privilège, port 8080, système de fichiers en lecture seule possible.
FROM ${NGINX_IMAGE} AS execution
ARG ESPACE
ARG APPLICATION
ENV NGINX_ENVSUBST_OUTPUT_DIR=/tmp/sira \
    API_AMONT="" \
    RESOLVEUR=127.0.0.11 \
    TAILLE_MAX_CORPS=1m
COPY deploiement/nginx/nginx.conf /etc/nginx/nginx.conf
COPY deploiement/nginx/en-tetes.conf /etc/nginx/sira/en-tetes.conf
COPY deploiement/nginx/en-tetes-page.conf /etc/nginx/sira/en-tetes-page.conf
COPY deploiement/nginx/sira-web.conf.template /etc/nginx/templates/sira-web.conf.template
COPY --chmod=755 deploiement/nginx/15-configuration.sh /docker-entrypoint.d/15-configuration.sh
COPY --from=build /src/${ESPACE}/dist/${APPLICATION}/browser /usr/share/sira/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/sante || exit 1
