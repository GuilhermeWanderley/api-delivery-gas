# ===== Estágio de build =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copia o pom primeiro para aproveitar o cache de dependências do Docker
COPY pom.xml .
RUN mvn -q -DskipTests dependency:resolve

# Copia o código e gera o jar executável
COPY src ./src
RUN mvn -q -DskipTests package

# ===== Estágio de execução =====
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Usuário sem privilégios de root (boa prática)
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
