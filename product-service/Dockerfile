# Build the jar first from the repo root:
#   mvn clean package -DskipTests
# then:
#   docker compose --profile infra --profile apps up -d --build
#
# A copy-the-jar image rather than a multi-stage Maven build: it keeps the
# image small, keeps the build reproducible from the jar you actually tested,
# and does not re-download the dependency tree inside Docker.
FROM eclipse-temurin:8-jre

LABEL org.opencontainers.image.title="oms-product-service"
LABEL org.opencontainers.image.version="1.0.0"

RUN groupadd --system oms && useradd --system --gid oms oms
RUN mkdir -p /var/log/oms && chown -R oms:oms /var/log/oms

WORKDIR /app
COPY target/product-service.jar /app/app.jar
RUN chown -R oms:oms /app

USER oms
EXPOSE 8082

ENV JAVA_OPTS="-Xms128m -Xmx320m"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD ["sh", "-c", "exec 3<>/dev/tcp/127.0.0.1/8082 || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
