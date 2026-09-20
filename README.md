# ShortLink

Microservices URL shortener with real-time analytics (Kafka + Elasticsearch)
and caching (Redis). Built as a portfolio project — see each service's own
README for details and interview-prep talking points.

## Structure

This is a Maven multi-module monorepo: one root `pom.xml` manages the shared
Spring Boot/Java version and build config, each service is a module with its
own `pom.xml` (inheriting from the root) and its own `application.yml`.

```
ShortLink/
├── pom.xml                 ← parent POM, shared version management
├── docker-compose.yml      ← infra (Mongo, Redis, ...) + every service
├── url-service/            ← Step 1-2: CRUD, redirect, Redis cache-aside
│   ├── pom.xml              (inherits from ../pom.xml)
│   └── src/main/resources/application.yml
└── (more services land here as sibling folders: analytics-service,
     notification-service, api-gateway, auth-service, ...)
```

## Building

From the root, `mvn clean install` builds every module in dependency order.
From inside a single module's folder (e.g. `url-service/`), `mvn clean
install` builds just that one — Maven resolves the parent POM via the
relative path either way.

## Running everything

```bash
docker compose up --build
```
from the repo root brings up Mongo, Redis, and every service that has a
compose entry. See individual service READMEs for running just one service
locally against Docker-hosted infra.
