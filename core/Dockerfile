# syntax=docker/dockerfile:1
# core image: Maven build on a JDK, runtime on a JRE (both Eclipse Temurin 25).
# The build context is the core/ folder: docker build -t wallet-core core

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# The cache mount keeps the downloaded dependencies between builds without putting them in a layer.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B package -DskipTests \
    && cp target/core-*.jar /build/app.jar

FROM eclipse-temurin:25-jre
# Not root: the process only reads its own jar.
RUN groupadd --system --gid 10001 wallet \
    && useradd --system --uid 10001 --gid wallet --no-create-home --shell /usr/sbin/nologin wallet
WORKDIR /app
COPY --from=build --chown=wallet:wallet /build/app.jar app.jar
USER wallet
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
