FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system pulse && useradd --system --gid pulse --create-home pulse \
    && mkdir -p /app/data \
    && chown -R pulse:pulse /app
COPY --from=build --chown=pulse:pulse /app/target/*.jar app.jar
USER pulse
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
