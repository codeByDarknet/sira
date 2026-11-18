# Keycloak de Sira : extension « code à usage unique », thème et royaumes du socle.
# Aucun secret dans l'image : mots de passe et certificats sont lus au démarrage dans /run/sira.
# Versions exactes, épinglées par empreinte.
ARG KEYCLOAK_VERSION=26.8.1@sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332
ARG JDK_IMAGE=eclipse-temurin:21.0.13_9-jdk-alpine-3.24@sha256:82801f9dcdcbad5a05082aba8c86d1da5b72ccb498e71318b1f91b5a477887b3

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS keycloak

# L'extension se compile contre les bibliothèques de l'image visée : ni Maven ni téléchargement.
# Horodatage fixe : deux constructions des mêmes sources donnent le même JAR.
FROM ${JDK_IMAGE} AS extension
COPY --from=keycloak /opt/keycloak/lib/lib/main /keycloak
COPY extension /extension
RUN mkdir /classes \
 && javac --release 21 -encoding UTF-8 -Xlint:all -Werror -proc:none -cp "/keycloak/*" -d /classes \
      $(find /extension/src -name '*.java' | sort) \
 && cp -r /extension/ressources/. /classes/ \
 && jar --create --date=2026-01-01T00:00:00Z --file /sira-identite.jar -C /classes .

FROM keycloak
COPY --from=extension /sira-identite.jar /opt/keycloak/providers/sira-identite.jar
COPY theme/ /opt/keycloak/themes/
COPY royaumes/ /opt/keycloak/royaumes/
COPY --chmod=0555 demarrer.sh /opt/keycloak/bin/sira-demarrer.sh

# Options figées à la construction : le démarrage (« start --optimized ») n'écrit plus dans l'image.
# Keycloak ne parle que HTTP, sur un réseau fermé : le TLS mutuel des serveurs d'application est
# terminé par « acces-identite », qui lui transmet le certificat vérifié (fournisseur « nginx »).
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_SPI_X509CERT_LOOKUP__PROVIDER=nginx
RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/sira-demarrer.sh"]
CMD ["start", "--optimized", "--import-realm"]
