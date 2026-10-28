FROM maven:3.9-eclipse-temurin-17
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests
EXPOSE 7070
CMD ["java", "-jar", "target/chilimba-tracker-1.0-SNAPSHOT.jar"]