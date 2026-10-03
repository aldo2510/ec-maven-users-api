# Despliegue de aplicación Java en AWS con Jenkins, ECR y ECS Fargate

## 1. Objetivo

Este documento explica cómo configurar el despliegue de la aplicación Java de este repositorio hacia AWS utilizando:

- **Maven** para compilar la aplicación.
- **Docker** para construir la imagen.
- **Amazon ECR** para almacenar la imagen.
- **Amazon ECS con Fargate** para ejecutar el contenedor.
- **Jenkins** para automatizar el proceso CI/CD.

El flujo será:

```text
GitHub
   |
   v
Jenkins
   |
   +--> Maven Build
   |
   +--> Docker Build
   |
   +--> Amazon ECR
   |       |
   |       v
   |   Docker Image
   |
   +--> Amazon ECS
           |
           v
       AWS Fargate
           |
           v
      Spring Boot API
```

---

# 2. Prerrequisitos

Antes de ejecutar el pipeline se necesita:

- Una cuenta de AWS.
- Un usuario/rol de AWS con permisos para ECR y ECS.
- Jenkins funcionando correctamente.
- Un agente Jenkins capaz de ejecutar Docker.
- Docker instalado en el agente o acceso al Docker socket.
- El repositorio clonado/configurado en Jenkins.
- Java 17/Maven, aunque Maven se ejecutará mediante el contenedor definido en el pipeline.

---

# 3. Crear las credenciales de AWS

Para este laboratorio el pipeline utiliza dos credenciales:

- `aws-access-key-id`
- `aws-secret-access-key`

En Jenkins ingresar a:

```text
Manage Jenkins
  -> Credentials
     -> System
        -> Global credentials
```

Crear:

### Credencial 1

Tipo:

```text
Secret text
```

ID:

```text
aws-access-key-id
```

Secret:

```text
<ACCESS_KEY_ID>
```

### Credencial 2

Tipo:

```text
Secret text
```

ID:

```text
aws-secret-access-key
```

Secret:

```text
<SECRET_ACCESS_KEY>
```

> Recomendación: para un entorno productivo es preferible utilizar un IAM Role/Instance Profile, OIDC o credenciales temporales antes que mantener Access Keys permanentes.

---

# 4. Crear el repositorio Amazon ECR

Ingresar a AWS y abrir:

**Amazon Elastic Container Registry (ECR)**

Crear un repositorio llamado:

```text
mi-application
```

También se puede crear automáticamente desde el pipeline si no existe.

La URL tendrá una estructura similar a:

```text
123456789012.dkr.ecr.us-east-1.amazonaws.com/mi-application
```

Donde:

- `123456789012` = AWS Account ID.
- `us-east-1` = región.
- `mi-application` = nombre del repositorio.

---

# 5. Configurar el AWS Account ID

Editar `jksfile-aws`.

Actualmente contiene:

```groovy
ECR_ACCOUNT_ID = '<AWS_ACCOUNT_ID>'
```

Reemplazarlo por el Account ID real:

```groovy
ECR_ACCOUNT_ID = '123456789012'
```

También se puede obtener mediante:

```bash
aws sts get-caller-identity
```

---

# 6. Configurar la región

El pipeline actualmente utiliza:

```groovy
AWS_REGION = 'us-east-1'
```

Si se desea otra región, cambiar este valor.

Por ejemplo:

```groovy
AWS_REGION = 'us-west-2'
```

Es importante que ECR y ECS utilicen la misma región para este laboratorio.

---

# 7. Crear el cluster ECS

Ir a:

**AWS Console -> ECS -> Clusters**

Crear un cluster:

```text
mi-application-cluster
```

Seleccionar la opción basada en AWS Fargate/Serverless.

El objetivo es que ECS administre la ejecución del contenedor sin necesidad de administrar servidores EC2.

---

# 8. Crear la Task Definition

En ECS crear una nueva **Task Definition**.

Configuración recomendada para este laboratorio:

```text
Family:
mi-application-task

Launch type:
AWS Fargate

Operating system:
Linux

CPU:
0.5 vCPU

Memory:
1 GB
```

Crear un container:

```text
Container name:
mi-application

Image:
123456789012.dkr.ecr.us-east-1.amazonaws.com/mi-application:latest

Container port:
8080
```

La aplicación Spring Boot expone:

```text
8080
```

porque el Dockerfile contiene:

```dockerfile
EXPOSE 8080
```

---

# 9. Configurar la red

La Task Definition/Service debe ejecutarse dentro de una VPC.

Seleccionar:

- VPC.
- Subnets.
- Security Group.

Para un laboratorio se puede utilizar una VPC existente con subnets públicas, siempre que la configuración de red permita acceder al servicio.

El Security Group debe permitir el tráfico necesario hacia el puerto:

```text
8080
```

Si posteriormente se utiliza un Application Load Balancer, lo recomendable es permitir:

```text
Internet
   |
   v
Load Balancer :80/:443
   |
   v
ECS Task :8080
```

En un ambiente productivo no se recomienda exponer directamente el puerto 8080 de la tarea a Internet.

---

# 10. Crear el ECS Service

Dentro del cluster:

```text
mi-application-cluster
```

Crear un Service:

```text
Service name:
mi-application-service
```

Utilizar:

```text
Launch type:
Fargate

Task definition:
mi-application-task

Desired tasks:
1
```

Para un laboratorio una tarea es suficiente.

---

# 11. Configurar el pipeline

El archivo utilizado para AWS es:

```text
jksfile-aws
```

La configuración principal es:

```groovy
APP_NAME       = 'mi-application'
AWS_REGION     = 'us-east-1'

ECR_ACCOUNT_ID = '<AWS_ACCOUNT_ID>'
ECR_REPOSITORY = 'mi-application'

ECS_CLUSTER    = 'mi-application-cluster'
ECS_SERVICE    = 'mi-application-service'
```

Ajustar estos valores de acuerdo con los recursos creados en AWS.

---

# 12. Flujo del pipeline

## Stage 1 - Build con Maven

Jenkins utiliza:

```text
maven:3.9.6-eclipse-temurin-17
```

Ejecuta:

```bash
mvn clean package -DskipTests
```

Esto genera el JAR dentro de:

```text
target/
```

El artefacto también se guarda mediante Jenkins:

```groovy
archiveArtifacts artifacts: 'target/*.jar'
```

---

# 13. Stage 2 - Docker Build

Jenkins recupera el JAR y ejecuta:

```bash
docker build -t <ECR_IMAGE> .
```

El Dockerfile utiliza:

```dockerfile
FROM eclipse-temurin:17-jdk-alpine-3.23
```

y copia el JAR:

```dockerfile
COPY target/*.jar /app/app.jar
```

La aplicación se ejecuta con:

```dockerfile
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

---

# 14. Stage 3 - Login en Amazon ECR

El pipeline obtiene un token de autenticación mediante:

```bash
aws ecr get-login-password --region "$AWS_REGION"
```

y lo utiliza para autenticarse:

```bash
docker login   --username AWS   --password-stdin "$ECR_REGISTRY"
```

Después realiza:

```bash
docker push "$IMAGE_NAME"
```

Por ejemplo:

```text
123456789012.dkr.ecr.us-east-1.amazonaws.com/mi-application:25
```

El tag utiliza el número de build de Jenkins:

```text
BUILD_NUMBER
```

Esto permite identificar exactamente qué build fue desplegado.

---

# 15. Stage 4 - Deploy a ECS/Fargate

Después de publicar la imagen, Jenkins ejecuta:

```bash
aws ecs update-service   --cluster "$ECS_CLUSTER"   --service "$ECS_SERVICE"   --force-new-deployment
```

Esto fuerza a ECS a realizar un nuevo deployment del servicio.

Luego Jenkins espera:

```bash
aws ecs wait services-stable
```

Por lo tanto, el pipeline no termina inmediatamente después de solicitar el deployment: espera a que ECS indique que el servicio está estable.

---

# 16. Ejecución solamente desde main

El build y push/deployment hacia AWS están condicionados a la rama:

```text
main
```

La condición utilizada es:

```groovy
b == 'main' ||
b == 'origin/main' ||
b == 'refs/heads/main' ||
b.endsWith('/main')
```

Esto permite ejecutar el pipeline desde diferentes representaciones de la rama principal.

---

# 17. Ejecutar el pipeline

Crear/configurar un Pipeline en Jenkins apuntando al repositorio:

```text
https://github.com/aldo2510/ec-maven-users-api
```

Configurar el Jenkinsfile:

```text
jksfile-aws
```

Ejecutar el pipeline.

El resultado esperado es:

```text
1. Checkout
      |
2. Maven Build
      |
3. Docker Build
      |
4. Login ECR
      |
5. Docker Push
      |
6. ECS Update Service
      |
7. ECS Wait Services Stable
      |
8. SUCCESS
```

---

# 18. Validar la imagen en ECR

En AWS:

```text
ECR
 -> Repositories
    -> mi-application
```

Deberían aparecer los tags generados por Jenkins:

```text
1
2
3
4
...
```

Cada número representa un `BUILD_NUMBER` de Jenkins.

---

# 19. Validar el deployment en ECS

Ir a:

```text
ECS
 -> Clusters
    -> mi-application-cluster
       -> Services
          -> mi-application-service
```

Validar:

```text
Desired tasks: 1
Running tasks: 1
Pending tasks: 0
```

El deployment debería aparecer como:

```text
PRIMARY
```

y en estado estable.

---

# 20. Validar los logs

Los logs pueden enviarse a **Amazon CloudWatch Logs** configurando el log driver en la Task Definition.

Una configuración típica es:

```text
Log group:
 /ecs/mi-application

Stream prefix:
 ecs
```

Esto permite visualizar los logs de Spring Boot desde:

```text
CloudWatch
 -> Log groups
 -> /ecs/mi-application
```

---

# 21. Validación de la API

Una vez que la tarea esté ejecutándose, validar el endpoint de la aplicación.

Por ejemplo:

```bash
curl http://<HOST>:8080
```

Si se utiliza un Application Load Balancer:

```bash
curl http://<ALB-DNS-NAME>
```

La URL exacta dependerá de cómo se haya configurado el acceso al servicio.

---

# 22. Permisos IAM mínimos

El usuario/rol utilizado por Jenkins necesita permisos para:

### Amazon ECR

```text
ecr:GetAuthorizationToken
ecr:BatchCheckLayerAvailability
ecr:CompleteLayerUpload
ecr:InitiateLayerUpload
ecr:PutImage
ecr:UploadLayerPart
ecr:DescribeRepositories
ecr:CreateRepository
```

### Amazon ECS

```text
ecs:DescribeClusters
ecs:DescribeServices
ecs:UpdateService
```

Para producción se recomienda crear una política IAM específica con el principio de mínimo privilegio, en lugar de utilizar permisos amplios como `AdministratorAccess`.

---

# 23. Estructura final

El repositorio queda con tres alternativas de despliegue:

```text
ec-maven-users-api/
│
├── Dockerfile
├── pom.xml
├── Jenkinsfile
├── jksfile-gcloud
├── jksfile-aws
└── README.md
```

### Azure

```text
Jenkins
  -> Azure Container Registry
  -> Azure Container Instances
```

### Google Cloud

```text
Jenkins
  -> Artifact Registry
  -> Cloud Run
```

### AWS

```text
Jenkins
  -> Amazon ECR
  -> ECS
  -> Fargate
```

---

# 24. Resultado

Con esta configuración, un cambio que llegue a `main` puede recorrer automáticamente:

```text
Developer
    |
    v
GitHub
    |
    v
Jenkins
    |
    +--> Maven Build
    |
    +--> Docker Build
    |
    +--> Amazon ECR
    |
    +--> ECS Service
    |
    v
AWS Fargate
    |
    v
Spring Boot API
```

Esto permite utilizar el mismo proyecto como laboratorio de despliegue **multi-cloud**, manteniendo una estrategia CI/CD similar para Azure, Google Cloud y AWS.
