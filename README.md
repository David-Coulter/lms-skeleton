# LMS service skeleton

Shared starting point for the team's Learning Management System. It's a Spring
Boot 3.5 / Java 21 service template matching our architecture diagram, plus a
generator that stamps out any of the eight services with its port, database and
API path already filled in.

**Every service runs standalone** — own container, own Postgres database, no
gateway, no Eureka, no Keycloak required. Nobody is blocked on anybody else's
work, and you can demo your service on its own.

Grab your service, get it running, then replace the placeholder entity with
your real ones.

---

## Quickstart

Needs Docker Desktop running. Nothing else — Java and Maven build inside the
container.

    git clone <this repo>
    cd lms-skeleton

    bash new-service.sh                     # lists the eight services
    bash new-service.sh learning-service    # creates ./learning-service

    docker compose up --build learning-service

First build takes a few minutes while Maven downloads dependencies. When you see
`Started LearningServiceApplication`, check it from another terminal:

    curl localhost:8105/actuator/health
    curl localhost:8105/api/learning

    docker compose exec postgres psql -U lms -d learningdb -c '\dt'

Health `UP`, a JSON array back, and an `items` table in your database means
you're done — container, service and database are all talking.

---

## Conventions

Taken from the architecture diagram. If the team changes any of these, edit the
table at the top of `new-service.sh` and regenerate rather than hand-editing
files.

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

Ports are published to localhost so you can curl them directly. In the real
deployment they'd only be on the internal network, with the gateway as the
single entry point.

---

## Layout

    template/              the skeleton, with placeholders
    new-service.sh         stamps out one service from the template
    docker-compose.yml     postgres + all eight services
    postgres/init.sql      creates one database per service
    postman-collection.json  CRUD tests
    .env                   profile + database credentials
    academic-service/      already generated, as a worked example

`docker compose up --build <name>` only builds what you name, so the services
nobody has generated yet are simply ignored.

---

## Making it yours

A generated service has the three-layer stack Phase 1 asks for:

    Item  ->  ItemRepository  ->  ItemService  ->  ItemController

`Item` is a throwaway that exists only to prove the database wiring works.
Replace it with your bounded context's real entities — everything around it
stays as is.

One pattern worth carrying over: `Item.ownerId` is a plain `Long`, not a JPA
relationship. **Cross-service references are IDs only.** Another service's
tables live in a different database, so there's nothing to join to. An
`Enrollment` holds a `studentId` and a `classId`, not object references.

---

## Profiles (Phase 1 requirement)

Both live in `application.yml` and differ visibly:

- **dev** — Hibernate creates the schema, SQL logging on, `data.sql` seed loaded
- **prod** — schema validated only, logging quiet, no seed data

<!-- -->

    PROFILE=dev  docker compose up --build learning-service
    PROFILE=prod docker compose up --build learning-service

`GET /api/<resource>/whoami` prints which profile is live — the easiest possible
proof for the video.

Prod uses `ddl-auto: validate` and fails fast if the schema doesn't exist yet.
That's intentional. Run dev once to create it, then switch. For a clean prod
demo with no seed rows left over, `docker compose down -v` wipes the volume
first.

---

## Postman

`postman-collection.json` covers health, the profile check, and a full
create / read / update / delete cycle — 14 assertions, including a 404 check
after the delete to prove the record is really gone.

Import it into Postman, or run it headless (Postman's GUI export is paywalled,
Newman isn't):

    npm install -g newman newman-reporter-htmlextra
    newman run postman-collection.json -r cli,htmlextra
    open newman/*.html

Point it at your own service by changing the `baseUrl` and `resource`
collection variables — e.g. `http://localhost:8105` and `learning`.

---

## Not wired up yet

Deliberately left off so the skeleton runs on its own. Each is one config change
away once the corresponding piece exists.

**Config service.** `spring.config.import` already points at
`config-service:8888`, marked `optional:` so it's inert until someone builds
one. Note Phase 1 explicitly requires a configuration service maintaining dev
and prod profiles, and there isn't one in the architecture diagram yet — this is
an open team item, not just a skeleton gap.

**Eureka.** Dependency and config are in place, but `EUREKA_ENABLED` is `false`
so nothing hunts for a registry that isn't running. Flip it to `true` in
`docker-compose.yml` once `discovery-service` exists. Your
`spring.application.name` has to match the `lb://<name>` in the gateway route or
it won't resolve.

**Keycloak.** Uncomment the resource-server dependencies in `pom.xml`, then set
`spring.security.oauth2.resourceserver.jwt.issuer-uri`.

One gotcha worth knowing before anyone spends an afternoon on it: Keycloak puts
realm roles in the `realm_access.roles` claim, which Spring Security ignores by
default. `@PreAuthorize("hasRole('TEACHER')")` will silently deny everything
until you add a custom `JwtAuthenticationConverter` that reads that claim. We
should agree on **one** converter and copy it into every service, so roles
behave identically everywhere.

---

## Troubleshooting

**`relation "items" does not exist` on startup.** `data.sql` ran before
Hibernate created the table. The dev profile sets
`spring.jpa.defer-datasource-initialization: true` to prevent this — if you
restructure `application.yml`, keep it.

**`invalid containerPort: __PORT__`** or other `__PLACEHOLDER__` text. The
generator's substitution didn't run. It now fails loudly instead of producing a
broken service, so regenerate: delete the folder and re-run `new-service.sh`.

**Port already in use.** Another service, or a previous run still up. `docker
compose ps` to see what's running, `docker compose down` to stop everything.

**Changed `application.yml` but nothing changed.** Rebuild, don't just restart —
config is baked into the jar: `docker compose up --build <name>`.

---

## Open questions for the team

- Who owns which service?
- Who's building the config service Phase 1 requires?
- Does the gateway use `StripPrefix` on its routes? That changes what path our
  controllers should map.
- One shared repo for all services, or one per person?
