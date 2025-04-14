# Corrections:


## Developers can trigger the pipeline with parameters (Environment name, MySQL password and MySQL port):

Here, developers will be able to provide:

``` bash

ENVIRONMENT_NAME: Name of the environment to be used for the image/container.

MYSQL_PASSWORD: Password of the MySQL root user (managed securely).

MYSQL_PORT: Port that will be mapped from the container to the host.
```

## Validation of the MYSQL_PORT parameter:

A step (“Validate Parameters”) has been added just after checkout, checking that the port is numeric and between 1 and 65535.

``` bash
stage('Validate Parameters') {
    steps {
        script {
            // Checks that the port is a valid number and within the range
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
```

## Correction to password substitution in the SQL file:

In the sed command, I replaced the placeholder <PASSWORD> (which was spelled wrong in the template file and has been corrected) using double quotes so that the Jenkins variable (params.MYSQL_PASSWORD) is correctly interpolated.

``` bash
    sh """
    sed 's/<PASSWORD>/${params.MYSQL_PASSWORD}/g' pipelines/include/create_developer.template > pipelines/include/create_developer.sql
    """
```

## Adjustment to the docker exec command:

Using double quotes for the commands inside /bin/bash -c ensures that the password is passed correctly.

``` bash
    sh """
    docker exec ${containerName} /bin/bash -c 'mysql --user="root" --password="$params.MYSQL_PASSWORD" < /scripts/create_developer.sql'
    """
```

## Active wait for MySQL initialization:

The 3-second sleep command has been inserted so that the container is ready to receive SQL commands and 
creates a loop for active waiting.

``` bash

    // Aguarda o MySQL estar pronto
    sh """
    echo "Waiting for MySQL to be ready inside container..."
    for i in {1..10}; do
        docker exec ${containerName} mysqladmin ping -h "localhost" --silent && break
        echo "Still waiting for MySQL..."
        sleep 3
    done
    """

```

## Spin up a container from the image built above, exposing the requested port on the Docker host:

Done like this:

``` bash
    sh """
    docker run -d --name ${containerName} --rm -e MYSQL_ROOT_PASSWORD=${params.MYSQL_PASSWORD} -p ${params.MYSQL_PORT}:3306 ${params.ENVIRONMENT_NAME}:latest
    """
```

## Prepare the environment by creating an account for the developer (username: developer, password: based on input parameter):

I run the sed command with the corrected placeholder in the template:

``` bash
    sh """
    sed "s/<PASSWORD>/${params.MYSQL_PASSWORD}/g" pipelines/include/create_developer.template > pipelines/include/create_developer.sql
    """
```

## The pipeline fails randomly in Jenkins

Corrections:

1) I create a standby with active verification in MySQL:

``` bash
    sh """
    until docker exec ${containerName} mysqladmin ping -h "localhost" --silent; do
        echo "Waiting for MySQL to be ready..."
        sleep 2
    done
    """
```

2) I check if the commands return an error and if so, I show the error message:

``` bash
    def status = sh(script: 'docker build ...', returnStatus: true)
    if (status != 0) {
        error("Docker build failed")
    }
```

3) I avoid port conflicts (port in use):

``` bash
    def portCheck = sh(script: "lsof -i :${params.MYSQL_PORT}", returnStatus: true)
    if (portCheck == 0) {
        error("Port ${params.MYSQL_PORT} is already in use")
    }
```

## If it works, developers are still not able to login into the running MySQL container because of an unknown issue. Please fix these issue before moving to the next part:

Corrected the placeholder in the script:

``` bash
CREATE USER IF NOT EXISTS 'developer'@'localhost' IDENTIFIED BY '<PASSWORD>';
```