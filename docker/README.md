# Unomi Docker Image

## Running Unomi
Unomi requires ElasticSearch so it is recommended to run Unomi and ElasticSearch using docker-compose:
```
docker-compose up
```
You will need to wait while Docker builds the containers and they boot up (ES will take a minute or two). Once they are up you can check that the Unomi services are available by visiting http://localhost:8181/cxs in a web browser.

## Environment variables

When you start the `unomi` image, you can adjust the configuration of the Unomi instance by passing one or more environment variables on the `docker run` command line.

- **`ELASTICSEARCH_HOST`** - The IP address of hostname for ElasticSearch
- **`ELASTICSEARCH_PORT`** - The port for ElasticSearch
