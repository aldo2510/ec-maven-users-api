FROM eclipse-temurin:17

# Crear usuario/grupo no-root con UID/GID numéricos
RUN groupadd -g 10001 app && \
    useradd  -u 10001 -g app -s /usr/sbin/nologin -m app

WORKDIR /app

# Copiar el jar y dejarlo con permisos del usuario no-root
COPY target/*.jar /app/app.jar
RUN chown -R app:app /app

# Ejecutar como no-root
USER 10001

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

#FROM openjdk:17-jdk-slim
#WORKDIR /app
#COPY target/*.jar app.jar
#ENTRYPOINT ["java", "-jar", "app.jar"]


