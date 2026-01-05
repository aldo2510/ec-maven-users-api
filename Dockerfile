FROM eclipse-temurin:17-jdk-alpine-3.23

# Crear usuario/grupo no-root con UID/GID numéricos (Alpine)
RUN addgroup -g 10001 -S app && \
    adduser  -u 10001 -S -G app -s /sbin/nologin -h /home/app app

WORKDIR /app

COPY target/*.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
