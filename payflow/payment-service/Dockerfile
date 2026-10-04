FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/payment-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
