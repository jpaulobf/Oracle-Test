pipeline {
    agent {
        label 'docker-host'
    }
    options {
        disableConcurrentBuilds()
        disableResume()
    }

    parameters {
        string name: 'ENVIRONMENT_NAME', trim: true     
        password defaultValue: '', description: 'Password to use for MySQL container - root user', name: 'MYSQL_PASSWORD'
        string name: 'MYSQL_PORT', trim: true  
        booleanParam(name: 'SKIP_STEP_1', defaultValue: false, description: 'STEP 1 - RE-CREATE DOCKER IMAGE')
    }

    stages {
        stage('Checkout GIT repository') {
            steps {
                script {
                    git branch: 'main',
                        credentialsId: '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',
                        url: 'https://github.com/jpaulobf/Oracle-Test.git'
                }
            }
        }
        
        stage('Validate Parameters') {
            steps {
                script {
                    // Verifica se a porta é um número válido e está dentro do intervalo
                    try {
                        def portNum = params.MYSQL_PORT.toInteger()
                        if (portNum < 1 || portNum > 65535) {
                            error "Invalid MySQL port: ${params.MYSQL_PORT}. It must be between 1 and 65535."
                        }
                    } catch (Exception e) {
                        error "MYSQL_PORT is not a valid number: ${params.MYSQL_PORT}"
                    }
                }
            }
        }

        stage('Create latest Docker image') {
            steps {
                script {
                    if (!params.SKIP_STEP_1) {
                        echo "Creating docker image with name ${params.ENVIRONMENT_NAME} using port: ${params.MYSQL_PORT}"

                        sh """
                        sed "s/<PASSWORD>/${params.MYSQL_PASSWORD}/g" pipelines/include/create_developer.template > pipelines/include/create_developer.sql
                        """
                        
                        def buildStatus = sh(script: "docker build pipelines/ -t ${params.ENVIRONMENT_NAME}:latest", returnStatus: true)
                        if (buildStatus != 0) {
                            error("Docker image build failed. Aborting pipeline.")
                        }
                    } else {
                        echo "Skipping STEP1"
                    }
                }
            }
        }

        stage('Start new container using latest image and create user') {
            steps {
                script {
                    // Verifica se a porta já está em uso
                    def portCheck = sh(script: "lsof -i :${params.MYSQL_PORT}", returnStatus: true)
                    if (portCheck == 0) {
                        error("Port ${params.MYSQL_PORT} is already in use. Aborting.")
                    }

                    def dateTime = sh(script: "date +%Y%m%d%H%M%S", returnStdout: true).trim()
                    def containerName = "${params.ENVIRONMENT_NAME}_${dateTime}"

                    // Sobe o container
                    def runStatus = sh(script: """
                        docker run -itd --name ${containerName} --rm \
                        -e MYSQL_ROOT_PASSWORD=${params.MYSQL_PASSWORD} \
                        -p ${params.MYSQL_PORT}:3306 ${params.ENVIRONMENT_NAME}:latest
                    """, returnStatus: true)
                    if (runStatus != 0) {
                        error("Failed to start Docker container. Aborting.")
                    }

                    // Aguarda o MySQL estar pronto
                    sh """
                    echo "Waiting for MySQL to be ready inside container..."
                    for i in {1..10}; do
                        docker exec ${containerName} mysqladmin ping -h "localhost" --silent && break
                        echo "Still waiting for MySQL..."
                        sleep 3
                    done
                    """

                    // Executa o script SQL
                    def execStatus = sh(script: """
                        docker exec ${containerName} /bin/bash -c 'mysql --user="root" --password="${params.MYSQL_PASSWORD}" < /scripts/create_developer.sql'
                    """, returnStatus: true)
                    if (execStatus != 0) {
                        error("Failed to execute SQL script inside container.")
                    }

                    echo "Docker container created: ${containerName}"
                }
            }
        }
    }
}
