# Unomi Docker Image

![Docker Pulls](https://img.shields.io/docker/pulls/mikeghen/unomi.svg)
 [![](https://images.microbadger.com/badges/version/mikeghen/unomi:1.3.svg)](https://microbadger.com/images/mikeghen/unomi:1.3 "Get your own version badge on microbadger.com")
# Running Unomi
Unomi requires ElasticSearch so it is recommended to run Unomi and ElasticSearch using docker-compose:
```
docker-compose up
```

# Environment variables

When you start the `unomi` image, you can adjust the configuration of the Unomi instance by passing one or more environment variables on the `docker run` command line.

- **`ELASTICSEARCH_HOST`** - The IP address of hostname for ElasticSearch
- **`ELASTICSEARCH_PORT`** - The port for ElasticSearch
