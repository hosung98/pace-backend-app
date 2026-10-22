FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 pace && useradd --uid 10001 --gid pace --no-create-home pace
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/build/libs/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
