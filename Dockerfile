FROM openjdk:17-alpine
FROM maven:latest as builder

WORKDIR /pdf-chat-app

COPY pom.xml ./
RUN mvn dependency:go-offline

COPY src ./src

RUN mvn package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/pdf-chat-0.0.1-SNAPSHOT.jar"]