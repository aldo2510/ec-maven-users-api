# AWS - Jenkins + ECR + ECS Express Mode

## Objetivo

Desplegar la aplicación Spring Boot de forma automatizada:

```text
GitHub -> Jenkins -> Maven -> Docker -> Amazon ECR -> ECS Express Mode -> Fargate
```

No se utiliza `latest`. Cada build publica una imagen inmutable usando `BUILD_NUMBER`, por ejemplo:

```text
mi-application:25
mi-application:26
mi-application:27
```

## 1. Prerrequisitos

- Cuenta AWS.
- Jenkins con un agente capaz de ejecutar Docker.
- Credenciales AWS configuradas en Jenkins.
- Dos roles IAM para ECS Express Mode.

El pipeline crea automáticamente el repositorio ECR y el servicio ECS Express Mode cuando no existen. Los roles IAM se crean manualmente una sola vez.

## 2. Credenciales AWS en Jenkins

Crear como `Secret text`:

```text
Credential ID: aws-access-key-id
Credential ID: aws-secret-access-key
```

Los valores deben corresponder a:

```text
aws-access-key-id      -> Access Key ID (AKIA... o ASIA...)
aws-secret-access-key  -> Secret Access Key
```

No se deben invertir las dos credenciales. El pipeline valida el formato de la Access Key ID antes de llamar a AWS.

Para producción se recomienda utilizar credenciales temporales, IAM Roles u OIDC cuando sea posible.

## 3. Crear los roles IAM

Para el despliegue mediante AWS CLI, los roles deben existir antes de ejecutar `create-express-gateway-service`.

Se crearán una sola vez:

```text
ecsTaskExecutionRole
ecsInfrastructureRoleForExpressServices
```

### 3.1 Crear ecsTaskExecutionRole

Crear el trust policy:

```bash
cat > ecs-task-execution-trust-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
```

Crear el role:

```bash
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://ecs-task-execution-trust-policy.json
```

Asignar la política administrada:

```bash
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

### 3.2 Crear ecsInfrastructureRoleForExpressServices

Crear el trust policy:

```bash
cat > ecs-express-infrastructure-trust-policy.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowAccessInfrastructureForECSExpressServices",
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
```

Crear el role:

```bash
aws iam create-role \
  --role-name ecsInfrastructureRoleForExpressServices \
  --assume-role-policy-document file://ecs-express-infrastructure-trust-policy.json
```

Asignar la política de Express Mode:

```bash
aws iam attach-role-policy \
  --role-name ecsInfrastructureRoleForExpressServices \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSInfrastructureRoleforExpressGatewayServices
```

### 3.3 Verificar los roles

```bash
aws iam get-role --role-name ecsTaskExecutionRole
```

```bash
aws iam get-role --role-name ecsInfrastructureRoleForExpressServices
```

Verificar las políticas:

```bash
aws iam list-attached-role-policies \
  --role-name ecsTaskExecutionRole
```

```bash
aws iam list-attached-role-policies \
  --role-name ecsInfrastructureRoleForExpressServices
```

> Estos roles se crean manualmente una sola vez. Jenkins no necesita permisos para crear o modificar roles IAM.

## 4. Configurar Account ID y región

En `jksfile-aws` y, si se utiliza, en `aws`, configurar la región y los ARN de los roles:

```groovy
AWS_REGION = 'us-east-2'
ECS_EXECUTION_ROLE_ARN = 'arn:aws:iam::<AWS_ACCOUNT_ID>:role/ecsTaskExecutionRole'
ECS_INFRASTRUCTURE_ROLE_ARN = 'arn:aws:iam::<AWS_ACCOUNT_ID>:role/ecsInfrastructureRoleForExpressServices'
```

En el archivo `aws` del repositorio, los ARN deben corresponder a la cuenta AWS donde se desplegará la aplicación.

El Account ID puede obtenerse con:

```bash
aws sts get-caller-identity
```

## 5. ECR se crea automáticamente

No es necesario crear el repositorio desde AWS Console.

Jenkins comprueba si existe:

```bash
aws ecr describe-repositories
```

y, si no existe, ejecuta:

```bash
aws ecr create-repository
```

Repositorio utilizado:

```text
mi-application
```

## 6.1 AWS CLI utilizada por Jenkins

El stage de ECR utiliza la imagen oficial de AWS CLI v2:

```text
public.ecr.aws/aws-cli/aws-cli:2.37.5
```

La imagen se ejecuta mediante Docker porque el agente utilizado para el build trabaja con `docker:24-cli`. El pipeline no instala `aws-cli` mediante Alpine.

El stage de deployment utiliza directamente la misma imagen de AWS CLI.

El agente Jenkins debe poder ejecutar Docker y tener acceso al socket:

```text
/var/run/docker.sock
```

## 6. Imagen versionada

Jenkins construye la imagen utilizando el número de build:

```text
<ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com/mi-application:<BUILD_NUMBER>
```

Por ejemplo:

```text
Build 42 -> mi-application:42
```

Esto permite identificar exactamente qué build fue desplegado y evita depender de `latest`.

## 7. Primer deployment

Después del push, Jenkins consulta si ya existe el servicio Express Mode.

Si no existe, crea automáticamente el servicio utilizando la imagen recién publicada:

```text
create-express-gateway-service
```

El servicio queda ejecutándose sobre Fargate y Express Mode administra los componentes necesarios de infraestructura.

Por tanto, no es necesario seleccionar manualmente una imagen desde la consola de ECS.

## 8. Deployments posteriores

Si el servicio ya existe, Jenkins actualiza la imagen:

```text
update-express-gateway-service
```

Ejemplo:

```text
Build 42 -> ECR :42 -> ECS Express Mode
Build 43 -> ECR :43 -> ECS Express Mode
Build 44 -> ECR :44 -> ECS Express Mode
```

Cada actualización utiliza una versión específica de la imagen.

## 9. Puerto y health check

La aplicación utiliza el puerto `8080`.

```dockerfile
EXPOSE 8080
```

El pipeline utiliza `/users` como health check porque la aplicación expone ese endpoint.

## 10. Etapas del pipeline

### Build con Maven

```bash
mvn clean package -DskipTests
```

### Build & Push a ECR

Jenkins:

1. Obtiene el Account ID.
2. Crea ECR si no existe.
3. Se autentica contra ECR.
4. Construye la imagen.
5. Etiqueta con `BUILD_NUMBER`.
6. Publica la imagen.

### Create / Update ECS Express Mode

Jenkins:

1. Busca el servicio.
2. Si no existe, lo crea.
3. Si existe, actualiza la imagen.
4. Solicita el deployment.

## 11. Deployment solamente desde main

El push a ECR y el deployment AWS se ejecutan solamente cuando la rama es:

```text
main
```

## 12. Permisos IAM de Jenkins

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

Permisos principales para Express Mode:

```text
ecs:ListServices
ecs:CreateExpressGatewayService
ecs:UpdateExpressGatewayService
ecs:DescribeExpressGatewayService
ecs:MonitorExpressGatewayService
```

Aplicar mínimo privilegio en ambientes reales.

Los permisos anteriores corresponden al usuario o rol cuyas credenciales utiliza Jenkins. Los permisos para crear y administrar la infraestructura de Express Mode corresponden al `ecsInfrastructureRoleForExpressServices`, que se configura por separado. AWS requiere un execution role y un infrastructure role para este tipo de servicio. citeturn0search0turn0search1

## 13. ¿Qué debemos hacer manualmente?

Una sola vez:

```text
1. Crear las dos credenciales AWS en Jenkins.
2. Crear los dos roles IAM siguiendo los scripts de este README.
3. Configurar Account ID y región en jksfile-aws.
4. Si se utiliza el archivo aws, revisar también los ARN de los roles en ese archivo.
5. Verificar que el endpoint /users responda correctamente para el health check.
```

No es necesario crear manualmente:

```text
- El repositorio ECR.
- El servicio ECS Express Mode.
- La infraestructura de red administrada por Express Mode.
```

Express Mode puede crear y administrar componentes como Application Load Balancer, target groups, security groups y políticas de auto scaling. citeturn0search0turn0search5

Después:

```text
git push
   |
   v
Jenkins
   |
   +--> Maven
   +--> Docker
   +--> ECR :BUILD_NUMBER
   +--> Create / Update ECS Express Mode
   |
   v
Fargate
   |
   v
Spring Boot API
```

## 14. Comparación multi-cloud

```text
Azure: Jenkins -> ACR -> Azure Container Instances
GCP:   Jenkins -> Artifact Registry -> Cloud Run
AWS:   Jenkins -> ECR -> ECS Express Mode -> Fargate
```

El patrón común es construir una imagen Docker, almacenarla en el registry del cloud y desplegarla en un runtime administrado.

## 15. Resultado

El pipeline AWS queda preparado para realizar el bootstrap del entorno y después actualizar automáticamente cada nueva versión:

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
    +--> ECR :BUILD_NUMBER
    +--> Create / Update ECS Express Mode
    |
    v
AWS Fargate
    |
    v
Spring Boot API
```

Cada build queda asociado a una imagen específica en ECR; no dependemos de `latest`.
