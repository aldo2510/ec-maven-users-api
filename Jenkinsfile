pipeline {
    agent { label 'controller' }

    environment {
        IMAGE_NAME = "mi-aplicacion-java"
        IMAGE_TAG = "latest"
        DOCKERFILE_PATH = "Dockerfile"
    }

    stages {
        stage('Compilar con Maven') {
            agent {
                docker { image 'maven:3.8.4-openjdk-17-slim' }
            }
            steps {
                sh 'mvn clean package'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, onlyIfSuccessful: true
            }
        }

        stage('Sonarqube') {
            agent {
                docker { image 'maven:3.8.4-openjdk-17-slim' }
            }
            steps {

                withSonarQubeEnv('sonar-server') {
                    sh 'mvn package org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                            -Dsonar.projectKey=labmaven01 \
                            -Dsonar.projectName=labmaven01 \
                            -Dsonar.sources=src/main \
                            -Dsonar.sourceEncoding=UTF-8 \
                            -Dsonar.language=java \
                            -Dsonar.tests=src/test \
                            -Dsonar.junit.reportsPath=target/surefire-reports \
                            -Dsonar.surefire.reportsPath=target/surefire-reports \
                            -Dsonar.jacoco.reportPath=target/jacoco.exec \
                            -Dsonar.java.binaries=target/classes \
                            -Dsonar.java.coveragePlugin=jacoco \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/jacoco.xml \
                            -Dsonar.exclusions=**/*IT.java,**/*TEST.java,**/*Test.java,**/src/it**,**/src/test**,**/gradle/wrapper** \
                            -Dsonar.java.libraries=target/*.jar'
                }
            }
        }
    

        stage('Ejecutar Tests') {
            agent {
                docker { image 'maven:3.8.4-openjdk-17-slim' }
            }
            steps {
                sh 'mvn test'
            }
        }

        stage('Build Image') {
            steps {
                copyArtifacts filter: 'target/*.jar',
                              fingerprintArtifacts: true,
                              projectName: '${JOB_NAME}',
                              flatten: true,
                              selector: specific('${BUILD_NUMBER}'),
                              target: 'target'
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -f ${DOCKERFILE_PATH} ."
            }
        }

        stage('Docker Run') {
            steps {
                script {
                    sh "docker run -d -p 8090:8080 ${IMAGE_NAME}:${IMAGE_TAG}"
                }
            }
        }
    }

    post {
        success {
            echo 'La compilación y las pruebas fueron exitosas.'
        }
        failure {
            echo 'Hubo un error en la compilación o las pruebas.'
        }
    }
}
