# LMS service skeleton

A Spring Boot 3.5 / Java 21 service template matching the team's architecture
diagram, plus a generator that stamps out any of the eight services with the
right port, database, and API path already filled in.

Each service runs standalone — its own container, its own database, no gateway,
no Eureka, no Keycloak needed. That's deliberate: Sanjay said services have to
work independently, and it means you aren't blocked on his shell.

## Layout

    template/            the service skeleton, with placeholders
    new-service.sh       stamps out one real service from the template
    docker-compose.yml   postgres + all eight services (builds only what you ask for)
    postgres/init.sql    creates one database per service
    .env                 profile + database credentials
    learning-service/    example already generated, delete if you don't need it

## Defaults, taken from the diagram

| service            | port | database     | path             |
|--------------------|------|--------------|------------------|
| user-service       | 8101 | userdb       | /api/users       |
| academic-service   | 8102 | academicdb   | /api/academic    |
| schedule-service   | 8103 | scheduledb   | /api/schedule    |
| enrollment-service | 8104 | enrollmentdb | /api/enrollment  |
| learning-service   | 8105 | learningdb   | /api/learning    |
| assessment-service | 8106 | assessmentdb | /api/assessment  |
| payment-service    | 8107 | paymentdb    | /api/payments    |
| analytics-service  | 8108 | analyticsdb  | /api/analytics   |

These are guesses from the diagram, not confirmed with Sanjay. If he comes back
with different conventions, edit the table at the top of `new-service.sh` and
regenerate — that's the whole reason it's a generator rather than a copy.

## Use it

    bash new-service.sh                     # lists the services
    bash new-service.sh learning-service    # creates ./learning-service

    docker compose up --build learning-service

    curl localhost:8105/actuator/health
    curl localhost:8105/api/learning
    curl -X POST localhost:8105/api/learning \
      -H 'Content-Type: application/json' \
      -d '{"name":"Test","description":"hello","ownerId":1}'

Confirm the table really exists in the right database:

    docker compose exec postgres psql -U lms -d learningdb -c '\dt'

## What's inside a generated service

The three-layer stack Phase 1 asks for: `Item` (entity) → `ItemRepository` →
`ItemService` → `ItemController`, with full CRUD. `Item` is a throwaway that only
proves the database wiring works. Replace it with your real entities once the
assignments land; everything around it stays.

One thing worth keeping from `Item`: the `ownerId` field is a plain `Long`, not a
JPA relationship. Cross-service references are IDs only, since the other service's
tables live in a different database.

## Profiles (Phase 1 requirement)

Both live in `application.yml` and differ visibly:

- **dev** — Hibernate creates the schema, SQL logging on, `data.sql` seed loaded
- **prod** — schema validated only, logging quiet, no seed data

    PROFILE=dev docker compose up --build learning-service
    PROFILE=prod docker compose up --build learning-service

`GET /api/learning/whoami` prints which profile is live — easy proof on camera.
Note prod fails fast if the schema doesn't exist yet, which is the point. Run dev
once to create it, then switch.

## Deliberately not wired up

- **Config service.** `spring.config.import` already points at
  `config-service:8888`, marked `optional:` so it's inert until someone builds
  one. Phase 1 requires a config service, and it's missing from the architecture
  diagram — worth raising with the team.
- **Eureka.** The dependency and config are in place but `EUREKA_ENABLED` is
  `false`, so the service doesn't hunt for a registry that isn't running. Flip it
  to `true` in compose once `discovery-service` exists.
- **Keycloak.** Uncomment the resource-server dependencies in `pom.xml`, then set
  `spring.security.oauth2.resourceserver.jwt.issuer-uri`. Keycloak puts realm
  roles in the `realm_access.roles` claim, which Spring ignores by default — you
  need a custom `JwtAuthenticationConverter` before
  `@PreAuthorize("hasRole('TEACHER')")` works. Agree on one converter as a team
  and copy it into every service.

## Status

Verified working on macOS (Apple silicon) with Docker Desktop: both profiles
start, CRUD works end to end, and the Postman collection passes 14/14
assertions.

## Postman

`postman-collection.json` covers health, the profile check, and a full
create / read / update / delete cycle with assertions.

Import it into Postman, or run it headless:

    npm install -g newman newman-reporter-htmlextra
    newman run postman-collection.json -r cli,htmlextra
    open newman/*.html

Change the `baseUrl` and `resource` collection variables to point at your own
service (e.g. `http://localhost:8105` and `learning`).
