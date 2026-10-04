FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/webhook-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms128m", "-Xmx256m", "-jar", "app.jar"]
