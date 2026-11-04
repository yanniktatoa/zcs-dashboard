FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Le .jar est produit par le job CI (mvn package -DskipTests)
COPY target/library-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
