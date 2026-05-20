FROM container-registry.oracle.com/graalvm/jdk:25 AS build
WORKDIR /workspace
RUN microdnf install -y maven && microdnf clean all
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM container-registry.oracle.com/graalvm/jdk:25
WORKDIR /app
COPY --from=build /workspace/target/copa-backend-1.0.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "/app/app.jar"]
