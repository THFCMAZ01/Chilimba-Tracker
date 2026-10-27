FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests
EXPOSE 7070
CMD ["java", "-jar", "target/chilimba-tracker-1.0-SNAPSHOT.jar"]