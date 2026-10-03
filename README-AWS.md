# AWS - Jenkins + ECR + ECS Express Mode

## Objetivo

Desplegar esta aplicación Spring Boot en AWS con un flujo sencillo:

```text
GitHub -> Jenkins -> Maven -> Docker -> Amazon ECR -> ECS Express Mode -> Fargate
```

ECS Express Mode permite evitar la configuración manual de un cluster ECS, Task Definition, Service, Load Balancer y gran parte del networking para este laboratorio.

## 1. Prerrequisitos

- Cuenta AWS.
- Jenkins con un agente capaz de ejecutar Docker.
- Credenciales AWS configuradas en Jenkins.
- Un repositorio ECR.
- Un servicio creado una sola vez mediante ECS Express Mode.

## 2. Credenciales Jenkins

Crear estas credenciales como `Secret text`:

```text
aws-access-key-id
aws-secret-access-key
```

Para producción, se recomienda utilizar credenciales temporales, IAM Roles u OIDC cuando sea posible.

## 3. Crear ECR

Crear el repositorio:

```text
mi-application
```

El pipeline también intenta crearlo automáticamente si no existe.

## 4. Crear el servicio ECS Express Mode

En AWS Console:

```text
Amazon ECS -> Express mode -> Create
```

Utilizar como imagen:

```text
<AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/mi-application:latest
```

Configuración sugerida:

```text
Service name: mi-application
Container port: 8080
CPU: 0.5 vCPU
Memory: 1 GB
```

El Dockerfile de este repositorio expone el puerto 8080.

Durante la creación, configurar los roles IAM solicitados por ECS Express Mode, incluyendo el rol que permita obtener la imagen privada desde ECR.

## 5. Configurar el ARN

Después de crear el servicio, copiar su ARN y modificar `jksfile-aws`:

```groovy
ECS_EXPRESS_SERVICE_ARN = '<ECS_EXPRESS_SERVICE_ARN>'
```

Ejemplo:

```text
arn:aws:ecs:us-east-1:123456789012:service/mi-application/mi-application
```

## 6. Configurar Account ID

En `jksfile-aws` reemplazar:

```groovy
ECR_ACCOUNT_ID = '<AWS_ACCOUNT_ID>'
```

por el Account ID real.

Se puede obtener con:

```bash
aws sts get-caller-identity
```

## 7. Pipeline Jenkins

`jksfile-aws` tiene tres etapas:

### Build con Maven

```bash
mvn clean package -DskipTests
```

### Docker Build & Push

Construye y publica:

```text
<AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/mi-application:latest
```

### Deploy

Jenkins solicita un nuevo deployment del servicio ECS:

```bash
aws ecs update-service --force-new-deployment
```

El servicio ECS Express Mode se encarga del despliegue sobre Fargate.

## 8. Condición de despliegue

El push a ECR y el deployment AWS se ejecutan solamente para `main`.

## 9. IAM de Jenkins

Permisos principales para ECR:

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

Para ECS:

```text
ecs:DescribeServices
ecs:UpdateService
```

Aplicar mínimo privilegio en ambientes reales.

## 10. Flujo final

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
   +--> Docker Build
   +--> Amazon ECR :latest
   |
   v
ECS Express Mode
   |
   +--> Fargate
   +--> Load Balancer / Networking gestionados
   |
   v
Spring Boot API
```

## 11. Comparación multi-cloud

```text
Azure:  Jenkins -> ACR -> Azure Container Instances
GCP:    Jenkins -> Artifact Registry -> Cloud Run
AWS:    Jenkins -> ECR -> ECS Express Mode -> Fargate
```

Con esto, AWS queda con un modelo mucho más parecido a Cloud Run sin tener que construir manualmente toda la arquitectura ECS tradicional.
