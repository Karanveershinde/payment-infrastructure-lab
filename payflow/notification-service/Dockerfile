FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/notification-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms128m", "-Xmx256m", "-jar", "app.jar"]
