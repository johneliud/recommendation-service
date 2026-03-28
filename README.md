# Recommendation Service

Movie recommendation engine for the Neo4flix platform — generates personalised movie suggestions using collaborative filtering and content-based filtering over the shared Neo4j graph.

## Requirements

| Tool | Version |
|------|---------|
| Java | 25 |
| Maven | 3.9+ |
| Neo4j | 5+ |

## Cloning

```bash
git clone https://github.com/johneliud/recommendation-service.git
cd recommendation-service
```

## Configuration

The service uses two properties files:

**`src/main/resources/application.properties`** — contains environment variable placeholders (committed).

**`src/main/resources/application-secrets.properties`** — contains actual values for local development (gitignored). Create it manually:

```properties
server.port=8085

spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=your_password

spring.data.neo4j.database=neo4j

jwt.secret=your_32_plus_char_secret_here
```

The `jwt.secret` must match the one used by the user-microservice — tokens are issued there and only validated here.

### Neo4j Setup

The recommendation service reads `Rating` and `Movie` nodes from the same Neo4j database used by the movie-service and rating-service. No additional schema setup is required — nodes are populated by those services.

## Running

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8085`.

## Testing

```bash
# All unit tests (no Neo4j instance required)
./mvnw test
```

## Docs

See [`docs/`](docs/) for:

- [Architecture](docs/architecture.md) — system position, package structure, recommendation algorithms, security model
- [API Reference](docs/api-reference.md) — all endpoints, request/response schemas, filter params
- [API Testing Guide](docs/api-testing.md) — Postman and curl examples for every endpoint