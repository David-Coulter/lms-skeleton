# LearningHub — service skeleton

Shared starting point for the team's Learning Management System: a Spring Boot
3.5 / Java 21 service template matching our architecture, a generator that
stamps out any service with its port, database and API path already filled in,
and a Spring Cloud Config Server serving per-service, per-profile config.

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

    bash new-service.sh                     # lists the services
    bash new-service.sh learning-service    # creates ./learning-service

    docker compose up --build learning-service

`postgres` and `config-service` start first; your service waits for the config
server's health check. The first build takes a few minutes while Maven
downloads dependencies. When you see `Started LearningServiceApplication`,
check it from another terminal:

    curl localhost:8105/actuator/health
    curl localhost:8105/api/learning

    docker compose exec postgres psql -U lms -d learningdb -c '\dt'

Health `UP`, a JSON array back, and an `items` table in your database means
container, service and database are all talking.

---

## Conventions

From the team architecture. If any of these change, edit the table at the top
of `new-service.sh` and regenerate rather than hand-editing files.

| service            | port | database     | path             | owner |
|--------------------|------|--------------|------------------|-------|
| user-service       | 8101 | userdb       | /api/users       | David Coulter |
| schedule-service   | 8103 | scheduledb   | /api/schedule    | Victoria Achom |
| enrollment-service | 8104 | enrollmentdb | /api/enrollment  | Renae Nicole Weiss |
| learning-service   | 8105 | learningdb   | /api/learning    | Jay Bruhn Schraml |
| assessment-service | 8106 | assessmentdb | /api/assessment  | Sai Sudha Piratla |
| payment-service    | 8107 | paymentdb    | /api/payments    | Victoria Achom |
| dashboard-service  | 8108 | —            | /api/dashboard   | Sanjay Chaudhuri |

`config-service` runs on 8888. `dashboard-service` owns no database — it
aggregates the other services over REST on each request.

Ports are published to localhost so you can curl them directly. In the real
deployment they'd only be on the internal network, with the gateway as the
single entry point.

---

## Layout

    template/                          the skeleton, with placeholders
    new-service.sh                     stamps out one service from the template
    config-service/                    Spring Cloud Config Server, port 8888
    docker-compose.yml                 postgres + config-service + the services
    postgres/init.sql                  creates one database per service
    user-service/                      David's service - a worked example
    postman-collection-user-service.json
    .env                               profile + database credentials

`docker compose up --build <name>` only builds what you name, so services
nobody has generated yet are simply ignored.

---

## Making it yours

A generated service has the three-layer stack Phase 1 asks for:

    Item  ->  ItemRepository  ->  ItemService  ->  ItemController

`Item` is a throwaway that exists only to prove the database wiring works.
Replace it with your bounded context's real entities — everything around it
stays as is. `user-service/` is a finished example if you want to see where
this ends up.

One pattern worth carrying over: `Item.ownerId` is a plain `Long`, not a JPA
relationship. **Cross-service references are ids only.** Another service's
tables live in a different database, so there's nothing to join to. An
`Enrollment` holds a `username` and a `courseId`, not object references.

In LearningHub the shared key is `username` — every service stores it as text,
and only user-service knows its own primary keys.

---

## Config service and profiles (Phase 1 requirement)

`config-service` serves `<service>-<profile>.yml` from
`config-service/src/main/resources/config/`, so the dev/prod difference lives in
one place instead of being duplicated in each service's jar.

Each service imports it with:

    spring.config.import: optional:configserver:${CONFIG_URL:http://config-service:8888}

`optional:` means the service still boots if the config server is down, falling
back to the profile blocks in its own `application.yml`.

| | dev | prod |
|---|---|---|
| Schema | Hibernate creates it | validated only |
| Seed data | loaded from `data.sql` | none |
| SQL logging | on | off |

<!-- -->

    PROFILE=dev  docker compose up --build user-service
    PROFILE=prod docker compose up --build user-service

`GET /api/<resource>/whoami` prints the active environment label, and that label
comes *from the config server* — so if it reads "served by config-service", the
configuration service is genuinely in the path rather than falling back to local
YAML. Easiest possible proof for the video.

Prod uses `ddl-auto: validate` and fails fast if the schema doesn't exist yet.
That's intentional: run dev once to create it, then switch. For a clean prod
demo with no seed rows left over, `docker compose down -v` wipes the volume
first.

To add your own service's config, drop `<your-service>-dev.yml` and
`<your-service>-prod.yml` next to the user-service ones.

---

## Postman

Each service has its own collection. `postman-collection-user-service.json` is
the worked example — 13 requests, 29 assertions covering the full CRUD cycle,
the directory and teacher lookups, and 404s for a deleted record and a bad
lookup.

Import it into Postman, or run it headless (Postman's GUI export is paywalled,
Newman isn't):

    npm install -g newman newman-reporter-htmlextra
    newman run postman-collection-user-service.json -r cli,htmlextra
    open newman/*.html

Change the `baseUrl` collection variable to point at your own service.

---

## Not wired up yet

Deliberately left off so services run on their own. Each is one config change
away once the corresponding piece exists.

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

**Flyway.** The team architecture specifies Flyway migrations (`V1` schema,
`V2` seed) with `ddl-auto: validate`. The skeleton currently uses Hibernate
auto-create plus `data.sql` in dev. Converting is `V1__schema.sql` and
`V2__seed.sql` per service.

---

## Troubleshooting

**`relation "..." does not exist` on startup.** `data.sql` ran before Hibernate
created the table. The dev profile sets
`spring.jpa.defer-datasource-initialization: true` to prevent this — if you
restructure `application.yml`, keep it.

**Duplicate key on a second `dev` start.** `data.sql` runs on every boot, so
seed inserts need to be idempotent — `ON CONFLICT ... DO NOTHING`, or a
`NOT EXISTS` guard. See `user-service/src/main/resources/data.sql`.

**`failed to lazily initialize a collection ... no Session`.** A `@OneToMany` is
lazy and `open-in-view` is off, so the session closes before Jackson serializes.
Either fetch eagerly or use `@EntityGraph` on the repository query.

**`invalid containerPort: __PORT__`** or other `__PLACEHOLDER__` text. The
generator's substitution didn't run. It now fails loudly instead of producing a
broken service, so regenerate: delete the folder and re-run `new-service.sh`.

**Port already in use.** Another service, or a previous run still up.
`docker compose ps` to see what's running, `docker compose down` to stop
everything.

**Changed `application.yml` but nothing changed.** Rebuild, don't just restart —
config is baked into the jar: `docker compose up --build <name>`.

---

## Services in this repo

- **[user-service](user-service/README.md)** — David Coulter. Profiles,
  directory, teacher lookups. Port 8101, `userdb`. See its README for the data
  model, endpoints and Phase 2 scope.

---

## Open questions for the team

- Which profile schema is authoritative — the slide deck's 24 columns with
  `grade_level`, or the database export's 26 with `grade` / `grade_number` /
  `academic_year`? user-service currently follows the deck.
- Does the gateway use `StripPrefix` on its routes? That changes what path our
  controllers should map.
- Is this repo the submission, or does everything move into
  `account4sanjay/learninghub`?
- Should the team adopt this config service, or is a different one planned?
