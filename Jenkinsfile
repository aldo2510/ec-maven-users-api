pipeline {
  agent { label 'controller' }

  environment {
    // --- Build / Docker ---
    IMAGE_NAME       = 'mi-aplicacion-java'
    IMAGE_TAG        = 'latest'
    DOCKERFILE_PATH  = 'Dockerfile'
    DOCKER_CREDS     = credentials('jksdockerregistryus2p01')   // crea DOCKER_CREDS_USR / DOCKER_CREDS_PSW
    ACR_REGISTRY     = 'laboratorio.azurecr.io'

    // --- Azure ACI ---
    APP_NAME         = 'myapp'
    ACI_NAME         = 'aci-myapp'          // minúsculas
    RESOURCE_GROUP   = 'mod3lab2'         // ajústalo
    LOCATION         = 'eastus'             // ajústalo
    CONTAINER_PORT   = '8080'

    // --- Service Principal (Secret Text) ---
    AZ_CLIENT_ID       = credentials('az-client-id')
    AZ_CLIENT_SECRET   = credentials('az-client-secret')
    AZ_TENANT_ID       = credentials('az-tenant-id')
    AZ_SUBSCRIPTION_ID = credentials('az-subscription-id')
  }

  options { timestamps() }

  stages {

    stage('Compilar con Maven') {
      agent { docker { image 'maven:3.9.6-eclipse-temurin-21' } }
      steps {
        sh 'mvn -B -DskipTests clean package'
        archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, onlyIfSuccessful: true
      }
    }

    stage('Build Image') {
      steps {
        copyArtifacts(
          projectName: env.JOB_NAME,
          selector: [$class: 'SpecificBuildSelector', buildNumber: "${env.BUILD_NUMBER}"],
          filter: 'target/*.jar',
          fingerprintArtifacts: true,
          flatten: true,
          target: 'target'
        )
        sh 'docker build -t ${ACR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} -f ${DOCKERFILE_PATH} .'
      }
    }

    stage('Publish Image') {
      steps {
        sh '''
          set -eux
          docker login ${ACR_REGISTRY} -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}
          docker tag ${ACR_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} ${ACR_REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER}
          docker push ${ACR_REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER}
          docker logout
        '''
      }
    }

    stage('Deploy to Azure Container Instances') {
      when {
        expression {
          def b = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: '').trim()
          b == 'main' || b == 'origin/main' || b == 'refs/heads/main' || b.endsWith('/main')
        }
      }
      agent {
        docker { image 'mcr.microsoft.com/azure-cli'; args '-u 0:0' }  // para evitar permisos
      }
      environment {
        IMAGE_REF        = "${ACR_REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER}"
        AZURE_CONFIG_DIR = "${WORKSPACE}/.azure"   // evita escribir en '/.azure'
        HOME             = "${WORKSPACE}"
      }
      steps {
        sh '''
          set -eux
          mkdir -p "$AZURE_CONFIG_DIR"
    
          # Login con Service Principal (usa forma con "=" por si la contraseña empieza con "-")
          az login --service-principal \
            --username="${AZ_CLIENT_ID}" \
            --password="${AZ_CLIENT_SECRET}" \
            --tenant="${AZ_TENANT_ID}"
'''
      }
    }
  }
}
