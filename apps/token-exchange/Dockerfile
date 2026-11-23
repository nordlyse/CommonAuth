FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/token-exchange.jar /app/token-exchange.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/token-exchange.jar"]
