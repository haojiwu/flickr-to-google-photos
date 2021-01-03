FROM maven:3.6.1-jdk-8 AS MAVEN_BUILD
WORKDIR /build
COPY ./ ./
RUN mvn clean package

ARG KEYSTORE_PATH
FROM openjdk:8-jre
WORKDIR /app
COPY --from=MAVEN_BUILD /build/target/flickr-to-google-photos-0.0.1-SNAPSHOT.jar ./app.jar
COPY $KEYSTORE_PATH ./
CMD ["java", "-Dspring.profiles.active=docker", "-jar", "./app.jar"]
