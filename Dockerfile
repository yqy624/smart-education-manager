FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/education-manager-1.0.0.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
