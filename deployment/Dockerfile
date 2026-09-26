# Contexto de build: la raiz del repo, no esta carpeta. Es lo que usa el disparador de Cloud Run
# (docker build -f deployment/Dockerfile .) y lo que filtra el .dockerignore de la raiz.
#   docker build -f deployment/Dockerfile -t financeapp-bk .
#
# Java de Microsoft (mcr.microsoft.com/openjdk) en las dos etapas, decision del usuario del
# 26-09-2026: recibe parches con frecuencia. No hay imagen solo-JRE, asi que el runtime es la
# distroless: sin shell ni gestor de paquetes, pero con el JDK completo.

FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet > /dev/null

# build y no solo bootJar: corre la suite y el umbral de JaCoCo, asi que un test rojo o la
# cobertura por debajo del minimo no producen imagen y el disparador no despliega.
COPY src src
RUN ./gradlew build --no-daemon

# Por capas: las dependencias no cambian entre commits, y Artifact Registry guarda cada capa una
# sola vez (el plan gratuito da 0,5 GB).
RUN cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM mcr.microsoft.com/openjdk/jdk:25-distroless
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# Sin shell no se puede crear un usuario: 'app' (uid 101) ya viene en la imagen distroless.
USER app

# El default del yaml es local: un despliegue tiene que arrancar en prod aunque olvide la variable.
ENV SPRING_PROFILES_ACTIVE=prod
# Cloud Run inyecta PORT y application.yaml lo lee; 8080 es el valor que usa si no llega.
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "--enable-native-access=ALL-UNNAMED", "org.springframework.boot.loader.launch.JarLauncher"]
