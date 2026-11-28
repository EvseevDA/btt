FROM node:24-bookworm-slim AS frontend
WORKDIR /build/btt-frontend
COPY btt-frontend/package*.json ./
RUN npm ci
COPY btt-frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY btt-api/ btt-api/
RUN mvn -B -ntp -f btt-api/pom.xml install
COPY btt-domain-logic-module/ btt-domain-logic-module/
COPY --from=frontend /build/btt-frontend/dist/ btt-domain-logic-module/src/main/resources/static/
RUN mvn -B -ntp -f btt-domain-logic-module/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /build/btt-domain-logic-module/target/btt-domain-logic-module-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
