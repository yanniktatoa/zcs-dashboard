FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Le .jar est produit par le job GitLab "package" (artefact target/*.jar)
COPY target/catalog-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
