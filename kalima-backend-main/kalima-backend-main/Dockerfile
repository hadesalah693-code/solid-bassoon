FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/kalima-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

# Cloud Run (1 GiB): override via JAVA_OPTS env var. Render free tier: use -Xmx256m instead.
ENV JAVA_OPTS="-XX:+UseContainerSupport -Xms256m -Xmx768m -XX:+UseG1GC"

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
