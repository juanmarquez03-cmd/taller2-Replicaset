# ---------------------------------------------------------------------------
# Etapa 1: compilar con Maven. No hace falta tener Java ni Maven instalados
# en la máquina: todo ocurre dentro de esta imagen temporal.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero solo el pom.xml: así Docker guarda en caché las dependencias y no
# las vuelve a descargar cada vez que cambia el código.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package

# ---------------------------------------------------------------------------
# Etapa 2: imagen final, solo con el JRE y el .jar (unos 100 MB comprimida).
# Alpine es más liviana para descargar en los workers por Tailscale.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios: si alguien comprometiera la app, no sería root.
RUN addgroup -S app && adduser -S -G app -u 10001 app
COPY --from=build /app/target/info-api.jar app.jar
USER 10001

EXPOSE 8080

# MaxRAMPercentage: el heap usa como máximo el 75 % del límite de memoria del
# contenedor (la JVM lo lee del cgroup). SerialGC gasta menos RAM que G1 y
# basta para una API pequeña en VMs de 2 GB.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
