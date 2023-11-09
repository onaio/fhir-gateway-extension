FROM maven:3.8.5-openjdk-17-slim as build

RUN apt-get update \
    && apt-get install -y nodejs npm \
    && npm install -g n && n stable

WORKDIR /app

COPY plugins/src ./plugins/src
COPY plugins/pom.xml ./plugins/
COPY exec/src ./exec/src
COPY exec/pom.xml ./exec/
COPY pom.xml .

RUN mvn --batch-mode package -Dlicense.skip=true -DskipTests -Dspotless.check.skip


FROM gcr.io/distroless/java17-debian12:nonroot as default

USER 65532:65532

COPY --from=build /app/exec/target/opensrp-gateway-plugin-exec.jar /app/
COPY resources/hapi_page_url_allowed_queries.json resources/hapi_page_url_allowed_queries.json
COPY resources/hapi_sync_filter_ignored_queries.json resources/hapi_sync_filter_ignored_queries.json

CMD ["/app/opensrp-gateway-plugin-exec.jar"]
