FROM maven:3.9.6-eclipse-temurin-17 AS build
COPY . /usr/app
WORKDIR /usr/app
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
VOLUME /tmp
COPY --from=build /usr/app/target/*.jar api-gateway.jar
ENTRYPOINT ["java","-jar","/api-gateway.jar"]
EXPOSE 8080

