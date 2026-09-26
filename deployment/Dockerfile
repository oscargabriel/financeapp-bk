# Contexto de build: la raiz del repo, no esta carpeta. Es lo que usa el disparador de Cloud Run
# (docker build -f deployment/Dockerfile .) y lo que filtra el .dockerignore de la raiz.
#   docker build -f deployment/Dockerfile -t financeapp-bk .

FROM eclipse-temurin:25-jdk AS build
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

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app

# El default del yaml es local: un despliegue tiene que arrancar en prod aunque olvide la variable.
ENV SPRING_PROFILES_ACTIVE=prod
# Cloud Run inyecta PORT y application.yaml lo lee; 8080 es el valor que usa si no llega.
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "--enable-native-access=ALL-UNNAMED", "org.springframework.boot.loader.launch.JarLauncher"]
