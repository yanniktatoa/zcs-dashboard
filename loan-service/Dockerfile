FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/loan-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
