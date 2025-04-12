# Correções:


## Developers can trigger the pipeline with parameters (Environment name, MySQL password and MySQL port):

Aqui, os desenvolvedores poderão fornecer:

``` bash

ENVIRONMENT_NAME: Nome do ambiente a ser usado para a imagem/container.

MYSQL_PASSWORD: Senha do usuário root do MySQL (gerenciada de forma segura).

MYSQL_PORT: Porta que será mapeada do container para o host.
```

## Validação do parâmetro MYSQL_PORT:

``` bash
Uma etapa ("Validate Parameters") foi adicionada logo após o checkout, verificando se a porta é numérica e se está entre 1 e 65535.
```

## Correção na substituição de senha no arquivo SQL:

No comando sed, substituí o placeholder <PASSWORD> usando aspas duplas para que a variável do Jenkins (params.MYSQL_PASSWORD) seja corretamente interpolada.

``` bash
    sh """
    sed 's/<PASSWORD>/${params.MYSQL_PASSWORD}/g' pipelines/include/create_developer.template > pipelines/include/create_developer.sql
    """
```

## Ajuste no comando docker exec:

Ao usar aspas duplas para os comandos dentro de /bin/bash -c, garante-se que a senha seja passada corretamente.

``` bash
    sh """
    docker exec ${containerName} /bin/bash -c 'mysql --user="root" --password="$params.MYSQL_PASSWORD" < /scripts/create_developer.sql'
    """
```

## Espera ativa para inicialização do MySQL:

O comando sleep de 3 segundos foi inserido para que o container esteja pronto para receber comandos SQL.

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

``` bash
    sh """
    docker run -d --name ${containerName} --rm -e MYSQL_ROOT_PASSWORD=${params.MYSQL_PASSWORD} -p ${params.MYSQL_PORT}:3306 ${params.ENVIRONMENT_NAME}:latest
    """
```

## Prepare the environment by creating an account for the developer (username: developer, password: based on input parameter):
``` bash
    sh """
    sed "s/<PASSWORD>/${params.MYSQL_PASSWORD}/g" pipelines/include/create_developer.template > pipelines/include/create_developer.sql
    """
```

## The pipeline fails randomly in Jenkins

Crio uma espera com verificação ativa no MySQL:

``` bash
    sh """
    until docker exec ${containerName} mysqladmin ping -h "localhost" --silent; do
        echo "Waiting for MySQL to be ready..."
        sleep 2
    done
    """
```

Checo se os comandos retornam erro e se for o caso, mostro a mensagem de erro:

``` bash
    def status = sh(script: 'docker build ...', returnStatus: true)
    if (status != 0) {
        error("Docker build failed")
    }
```

Evito conflito de porta (porta em uso):

``` bash
    def portCheck = sh(script: "lsof -i :${params.MYSQL_PORT}", returnStatus: true)
    if (portCheck == 0) {
        error("Port ${params.MYSQL_PORT} is already in use")
    }
```

## If it works, developers are still not able to login into the running MySQL container because of an unknown issue. Please fix these issue before moving to the next part:

Correção do Placeholder no script

``` bash
CREATE USER IF NOT EXISTS 'developer'@'localhost' IDENTIFIED BY '<PASSWORD>';
```