FROM maven:3.9.6-eclipse-temurin-17 AS build
COPY . /usr/app
WORKDIR /usr/app
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
VOLUME /tmp
COPY --from=build /usr/app/target/*.jar order-service.jar
ENTRYPOINT ["java","-jar","/order-service.jar"]
EXPOSE 8084

