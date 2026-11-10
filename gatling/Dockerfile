# Runs the Gatling simulation inside the compose network, so no Maven or JDK
# is needed on the host.
FROM maven:3.9-eclipse-temurin-21
WORKDIR /gatling
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
ENTRYPOINT ["mvn", "-B", "gatling:test"]
