# Autorité de certification interne de Sira : conteneur d'initialisation, sans processus permanent.
FROM alpine:3.24.3@sha256:71e9b7bae160ba167fa17414401b94cbcdaf3c32feeab9298bd794a2a0614c35
RUN apk add --no-cache openssl
COPY pki.sh /usr/local/bin/pki
RUN chmod 0555 /usr/local/bin/pki
ENTRYPOINT ["/usr/local/bin/pki"]
CMD ["initialiser"]
