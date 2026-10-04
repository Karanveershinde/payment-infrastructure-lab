FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/provider-simulator-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms128m", "-Xmx256m", "-jar", "app.jar"]
