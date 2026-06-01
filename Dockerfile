# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy POMs first so dependency resolution is cached separately from source.
COPY pom.xml .
COPY agent-core/pom.xml agent-core/
COPY mcp-client/pom.xml mcp-client/
COPY mcp-integration/pom.xml mcp-integration/
COPY agent-terminal/pom.xml agent-terminal/
RUN mvn -pl mcp-integration -am dependency:go-offline --no-transfer-progress -q

# Copy sources and package the mcp-integration fat jar.
COPY agent-core/src agent-core/src
COPY mcp-client/src mcp-client/src
COPY mcp-integration/src mcp-integration/src
RUN mvn -pl mcp-integration -am clean package -DskipTests --no-transfer-progress -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app

COPY --from=build /build/mcp-integration/target/mcp-integration-*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

# Override relative paths from application.yml with absolute paths valid inside the container.
# MCP_CONFIG_FILE: if absent, the server starts with no persisted servers.
# AGENT_TOOLS_ENABLED: disabled by default in cloud; set to true + mount a workspace if needed.
ENV MCP_CONFIG_FILE=/app/mcp-servers.json
ENV AGENT_TOOLS_ENABLED=false

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
