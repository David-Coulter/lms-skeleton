# LearningHub — user-service

**David Coulter · Phase 2: Security**

The *Identity & Profiles* service of LearningHub, now behind a gateway with
Keycloak authentication, role-based authorization, self-service registration
and password reset, service discovery through Eureka, and a Gatling load test.

---

## What runs

Seven containers on one Docker network. **The only port the outside world
needs is 8080.**

| | Port | Role |
|---|---|---|
| `gateway-service` | **8080** | Single origin. `/auth/**` → Keycloak, `/api/users/**` → `lb://user-service` |
| `keycloak` | *(internal)* | Identity provider. Realm `learninghub`, roles admin/teacher/student |
| `user-service` | *(internal)* | Profiles, directory, registration, password reminder |
| `discovery-service` | 8761 | Eureka registry |
| `config-service` | 8888 | Spring Cloud Config, dev/prod profiles |
| `postgres` | 5433 | One database per service, plus Keycloak's own |
| `mailpit` | 8025 | Fake SMTP — password-reset mail lands here |

`user-service` and `keycloak` publish no host port on purpose: everything
reaches them through the gateway, which is what Phase 2 requires, and it lets
`user-service` scale (a fixed host port can only be claimed by one container).

---

## Deploy and run

**Prerequisites:** Docker Desktop. Nothing else — Java and Maven build inside
the containers.

    git clone https://github.com/David-Coulter/lms-skeleton.git
    cd lms-skeleton
    docker compose up --build

First run takes several minutes (Maven dependencies for four services, plus
the Keycloak image). Keycloak needs ~40s on first boot to create its schema
and import the realm.

### Verify it came up

    # log in through the gateway
    TOKEN=$(curl -s -X POST \
      'http://localhost:8080/auth/realms/learninghub/protocol/openid-connect/token' \
      -d grant_type=password -d client_id=learninghub-spa \
      -d username=student1 -d password=student123 \
      | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

    curl -s -o /dev/null -w "teachers:  %{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/teachers
    curl -s -o /dev/null -w "directory: %{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/directory
    curl -s -o /dev/null -w "no token:  %{http_code}\n" http://localhost:8080/api/users/teachers

Expect **200 / 403 / 401**. That trio is authentication and authorization
working end to end.

Also useful:

- http://localhost:8761 — Eureka: `USER-SERVICE` and `GATEWAY-SERVICE` registered
- http://localhost:8025 — Mailpit inbox
- http://localhost:8080/auth/admin — Keycloak console (`admin` / `admin`)

### Test accounts

| Username | Password | Role |
|---|---|---|
| `student1` | `student123` | student |
| `gadams` | `student123` | student |
| `janderson` | `teacher123` | teacher |
| `admin1` | `admin123` | admin |

---

## The four security features

### 1. Authentication

Keycloak issues a signed JWT via the password grant, reached through the
gateway at `/auth/realms/learninghub/protocol/openid-connect/token`.
`user-service` never sees a password and never calls Keycloak to check a
token — it verifies the signature against the realm's JWKS. A tampered token
is rejected locally, with no network round trip.

Configured in `user-service/src/main/resources/application.yml`:

    spring.security.oauth2.resourceserver.jwt.issuer-uri   # the gateway's address
    spring.security.oauth2.resourceserver.jwt.jwk-set-uri  # fetched internally

### 2. Authorization

Keycloak puts realm roles in a nested `realm_access.roles` claim, which
Spring Security's default converter does not read. `KeycloakRoleConverter`
maps them to `ROLE_*` authorities; without it `@PreAuthorize` denies
everything, silently.

Rules on `ProfileController`:

| Endpoint | Who |
|---|---|
| `GET /api/users/profiles/**`, and all writes | `hasRole('ADMIN')` |
| `GET /api/users/directory` | `hasAnyRole('TEACHER','ADMIN')` |
| `GET /api/users/teachers/**` | `isAuthenticated()` |
| `POST /register`, `POST /password-reminder` | public |

The demonstration: a student gets **200** on `/teachers` and **403** on
`/directory`. Same URL, same code — only the role in the token differs.

### 3. User registration

`POST /api/users/register` — public, because you cannot hold a token before
you have an account. Creates the Keycloak account via the Admin API and the
local profile row in one call.

**The role is forced to `student` server-side.** Taking it from the request
body would let anyone register themselves as an admin.

### 4. Password reminder

`POST /api/users/password-reminder` — public. Triggers Keycloak's
`UPDATE_PASSWORD` action email; the mail arrives in Mailpit at
http://localhost:8025.

**Returns 202 whether or not the account exists.** A 404 for unknown users
would make this endpoint a username oracle — anyone could discover which
accounts are real. The miss is logged server-side instead.

---

## Bearer token across services

One token, issued once, accepted everywhere:

1. The SPA (or Postman) logs in at `/auth` and receives a JWT.
2. Every later request carries `Authorization: Bearer <token>`.
3. The gateway **forwards the header untouched** and does not validate it.
4. Each service verifies the signature itself against the realm's JWKS.

The gateway deliberately does not validate tokens. If something reaches a
service by another route, that service is still protected — security does not
depend on traffic having come through the front door.

Nothing stores a session. That statelessness is what lets `user-service` run
as several instances behind one gateway.

---

## Service discovery

`discovery-service` is a Netflix Eureka registry on 8761. `user-service` and
`gateway-service` register at startup; the gateway routes to
`lb://user-service` rather than a host and port.

Scale it:

    docker compose up -d --scale user-service=3

Eureka then lists three instances of `USER-SERVICE` and the gateway
load-balances across them. **No configuration changed anywhere** — that is
the point of discovery.

---

## Stress test

Gatling, run inside the compose network so nothing is needed on the host:

    docker compose --profile load run --rm gatling
    docker compose --profile load run --rm gatling -Dusers=400 -Dduration=120

Report: `gatling/target/gatling/<run>/index.html`

Each virtual user logs in once and reuses that token for 20 calls — the
realistic pattern, and the reason the service scales.

### Results — same 109,410 requests, 400 concurrent users

| | 1 instance | 3 instances |
|---|---|---|
| Failed requests | 1,435 (1.31%) | **0** |
| Throughput | 391 req/s | **688 req/s** |
| Mean response | 4,929 ms | **65 ms** |
| 95th percentile | 9,036 ms | **261 ms** |
| 99th percentile | 15,963 ms | **1,100 ms** |
| Max | 30,017 ms (timeout) | 5,135 ms |
| Assertions | all failed | **all passed** |

The load that saturates one instance runs clean on three, with no code or
configuration change — only `--scale`. The new instances registered
themselves and the gateway discovered them.

The simulation asserts under 1% failures, p95 under 1500 ms and mean under
500 ms, so a run passes or fails rather than just producing numbers.

---

## Postman

`CoulterDavid_Phase2_Postman.json` — six folders, run in order:

0. No token → 401
1. Authentication — password grant, three roles, wrong password
2. Authorization — same endpoints, different roles, 200 vs 403
3. Registration — public sign-up, duplicate rejected, new account logs in
4. Password reminder — reset email, and no account enumeration
5. Bearer token across services — reuse, statelessness, tampered token

Folder 1 stores tokens in collection variables that the rest reuse. Every
request targets `http://localhost:8080`, so the gateway requirement is
demonstrated by construction.

Headless:

    newman run CoulterDavid_Phase2_Postman.json -r cli,htmlextra

---

## Known gaps

- **The gateway does not rate-limit or circuit-break.** Resilience here is
  horizontal scaling, not bulkheading. Resilience4j on the gateway routes
  would be the next step.
- **Registration sends no verification email.** The account is enabled
  immediately; `emailVerified` is false but nothing enforces it.
- **Client secrets are in `docker-compose.yml`.** Fine for a local demo, not
  for anything real — they belong in a secret store.
- **`/directory` and `/teachers` return the full entity** rather than the
  team contract's trimmed shape. Response DTOs are the fix, and the same
  layer would make responses role-aware.
- **Seeding is `data.sql`**, not Flyway, which is what the team architecture
  specifies.

## Troubleshooting

See `PHASE2-TROUBLESHOOTING.md` — issuer mismatch, the role converter, port
clashes, Keycloak health, and the Admin API service account.
