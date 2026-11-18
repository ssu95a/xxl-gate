FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -ntp -U -s .mvn/settings.xml dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -U -s .mvn/settings.xml -DskipTests package

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home --uid 10001 xxl \
    && mkdir log && chown xxl log
COPY --from=build /build/target/XXL-Gate.jar app.jar
COPY application-xxlgate.yml ./
USER xxl
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
