FROM container-registry.oracle.com/graalvm/jdk:25 AS build
WORKDIR /workspace
RUN microdnf install -y maven && microdnf clean all
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline || true
COPY . ./
RUN mvn -q -Pproduction -DskipTests package

FROM container-registry.oracle.com/graalvm/jdk:25
WORKDIR /app
COPY --from=build /workspace/target/copa-frontend-1.0.0.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "--enable-preview", "-jar", "/app/app.jar"]
