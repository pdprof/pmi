FROM docker.io/openliberty/open-liberty:kernel-slim-java11-openj9-ubi

ARG VERSION=1.0
ARG REVISION=SNAPSHOT

LABEL \
  org.opencontainers.image.authors="Eiji SHIRASU" \
  org.opencontainers.image.vendor="IBM" \
  org.opencontainers.image.url="local" \
  org.opencontainers.image.source="https://github.com/pdprof/pmi/tree/master/collector-rest" \
  org.opencontainers.image.version="$VERSION" \
  org.opencontainers.image.revision="$REVISION" \
  vendor="Open Liberty" \
  name="collector-rest" \
  version="$VERSION-$REVISION" \
  summary="Sample client for the endpoints of restConnector-2.0 feature." \
  description="The image of DB workload emulator."

USER root

COPY --chown=1001:0 src/main/liberty/config/ /config/
RUN features.sh

COPY --chown=1001:0 target/*.war /config/dropins/

USER 1001
