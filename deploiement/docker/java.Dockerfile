# syntax=docker/dockerfile:1
# Image commune à toutes les applications Java de Sira.
# Contexte de build : la racine du dépôt. Exemple :
#   docker build -f deploiement/docker/java.Dockerfile --build-arg MODULE=passerelle/serveur -t sira/passerelle .

# Versions exactes, épinglées par empreinte.
ARG JDK_IMAGE=eclipse-temurin:21.0.13_9-jdk-alpine-3.24@sha256:82801f9dcdcbad5a05082aba8c86d1da5b72ccb498e71318b1f91b5a477887b3
ARG JRE_IMAGE=eclipse-temurin:21.0.13_9-jre-alpine-3.24@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474

# --- Compilation de tout le réacteur. Cette étape ne dépend pas de MODULE :
#     elle n'est exécutée qu'une fois, quel que soit le nombre d'images construites.
FROM ${JDK_IMAGE} AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY commun commun
COPY console console
COPY journal journal
COPY resilience resilience
COPY consentement-minimisation consentement-minimisation
COPY passerelle passerelle
COPY espace-usager espace-usager
COPY registres/serveur registres/serveur
COPY guichet-agent/serveur guichet-agent/serveur
# Le dépôt Maven local vit dans un cache BuildKit : rien n'est retéléchargé d'un build à l'autre.
# Les tests et le contrôle de mise en forme tournent avec « ./mvnw verify », pas dans l'image.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests -Dspotless.check.skip=true package

# --- Découpage du jar en couches : les dépendances changent rarement, le code souvent.
FROM ${JRE_IMAGE} AS couches
ARG MODULE
WORKDIR /opt/sira
COPY --from=build /src/${MODULE}/target/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extrait

# --- Exécution.
FROM ${JRE_IMAGE} AS execution
# Identifiant fixe : les fichiers de clés montés dans /run/sira/pki doivent être lisibles par 10001.
# Le code appartient à root : l'application le lit mais ne peut pas le modifier.
RUN addgroup -S -g 10001 sira && adduser -S -u 10001 -G sira -h /opt/sira -s /sbin/nologin sira
WORKDIR /opt/sira
COPY --from=couches /opt/sira/extrait/dependencies/ ./
COPY --from=couches /opt/sira/extrait/spring-boot-loader/ ./
COPY --from=couches /opt/sira/extrait/snapshot-dependencies/ ./
COPY --from=couches /opt/sira/extrait/application/ ./
USER 10001:10001
# Tomcat n'écrit que dans java.io.tmpdir : monter /tmp en tmpfs permet un système de fichiers en lecture seule.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
# 8443 : API en mTLS. 8080 : serveurs sans TLS entrant (derrière leur nginx). 8081 : gestion, boucle locale seulement.
EXPOSE 8443 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
