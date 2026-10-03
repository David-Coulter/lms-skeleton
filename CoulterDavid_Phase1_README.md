# LearningHub
## user-service
**David Coulter**

## Phase 1

This microservice owns the *Identity & Profiles* bounded context: the profile record 
for every person in the school, and the lookups other services and the SPA use to resolve
people by name, role and picture.

- Port = `8101`

- database = `userdb`

- base path = `/api/users`

---

## Deploy and run

**Prerequisites:** Docker Desktop. Nothing else — Java and Maven build inside
the container.

    git clone https://github.com/David-Coulter/lms-skeleton.git
    cd lms-skeleton
    docker compose up --build user-service

Three containers come up: `postgres`, `config-service` (the Spring Cloud Config
Server, port 8888), and `user-service` on 8101. The service waits on the config
server's health check before starting.

The first build takes a few minutes while Maven downloads dependencies. Wait
for `Started UserServiceApplication`, then from another terminal:

    curl localhost:8101/actuator/health
    curl localhost:8101/api/users/profiles
    curl localhost:8101/api/users/teachers

    docker compose exec postgres psql -U lms -d userdb -c '\dt'

Health `UP`, a populated JSON array, and `profile` / `profile_class` tables in
`userdb` means the whole path is working — container, service, config server
and database.

To stop: `docker compose down`, or `docker compose down -v` to also wipe the
database volume and start fresh.

### What's in the repository

    user-service/      this service
    config-service/    Spring Cloud Config Server, serves the dev and prod profiles
    docker-compose.yml postgres + config-service + the services
    postgres/init.sql  creates one database per service
    template/          service skeleton the team shares
    new-service.sh     generates a service from the template
---

## Canonical model

The whole context is a profile and the classes attached to it.

### `profile`

One row per person, student, teacher or admin. 
Columns are modelled based on the team's shared schema.

| Group | Fields |
|-------|--------|
| Identity | `id`, `username` (unique), `role` |
| Name | `full_name`, `title`, `first_name`, `last_name` |
| Contact | `email`, `phone`, `location`, `address` |
| Personal | `gender`, `date_of_birth`, `bio` |
| Academic | `school`, `grade_level` |
| Guardian | `guardian_name`, `guardian_relation`, `guardian_email`, `guardian_phone` |
| Preferences | `pref_timezone`, `pref_notifications` |

### `profile_class`

This is the class that a person is in. 
`profile_id` → `profile.id`, cascading on delete.

### Two decisions that were made:

**`username` is the system's identity, not `id`.** Every other service in
LearningHub stores `username` (or `teacher_username`) as text. Enrollment,
learning, assessment, payments. None of them know this service's primary key.
So `username` carries a unique constraint and is the join key across the whole
system, while `id` is purely local.

**`grade_level` holds enrollment-service's id, not a display name.** The column
stores `"junior"`, not `"Junior"` and whoever renders it resolves the name from
`/api/enrollment/grade-levels`. Storing the label instead would duplicate data
another context owns.

**Guardian and Preferences are `@Embeddable` value objects,** not tables. 
They don't have an identity of their own and don't have a lifecycle apart from the profile, so they
live in `profile`'s columns while appearing as nested objects in the API. Staff
profiles simply leave the guardian fields null.

---

## Endpoints

| Method | Path | Returns |
|--------|------|---------|
| `GET` | `/api/users/profiles` | All profiles |
| `GET` | `/api/users/profiles/{id}` | One profile, 404 if absent |
| `POST` | `/api/users/profiles` | 201 + `Location` |
| `PUT` | `/api/users/profiles/{id}` | Updated profile, 404 if absent |
| `DELETE` | `/api/users/profiles/{id}` | 204, 404 if absent |
| `GET` | `/api/users/directory` | All profiles, sorted role → last name → first name |
| `GET` | `/api/users/teachers` | Teacher profiles only |
| `GET` | `/api/users/teachers/{username}` | One teacher, 404 if not a teacher |
| `GET` | `/api/users/profiles/whoami` | The active profile's environment label |

The last three directory/teacher endpoints match the team's API contract. 
The `/profiles` CRUD surface satisfies Phase 1's "simple CRUD REST
functionality" requirement. 
In the finished system, profiles will be created through the 
Keycloak admin flow rather than a public POST.

---

## Layers

    ProfileController -> REST, HTTP status mapping
    ProfileService -> transactions, role constants, update semantics
    ProfileRepository -> Spring Data JPA with three derived queries
    Profile -> entity, with Guardian and Preferences embedded
    ProfileClass -> child entity

---

## Dev and prod profiles

    PROFILE=dev  docker compose up --build user-service
    PROFILE=prod docker compose up --build user-service

| | dev | prod |
|---|---|---|
| Schema | Hibernate creates it | validated only |
| Seed data | loaded from `data.sql` | none |
| SQL logging | on | off |

`GET /api/users/profiles/whoami` prints which profile is live, and the label comes from
the config server.

Prod fails fast when the schema doesn't exist, which is the point of
`validate`. Ensure that you run dev once to create it, then you can switch.

---

## Tests

    newman run CoulterDavid_Phase1_Postman.json -r cli,htmlextra

13 requests, 31 assertions: the full CRUD cycle, embedded objects round-trip,
the child collection serializing, and 404 errors for both a deleted profile, and a
username that isn't a teacher.

---

## Phase 2

Phase 1 asks for entities, repository, service and CRUD controllers, so the
following is deliberately out of scope:

- **The eight Keycloak Admin endpoints** (`/admin/users/**`) — create, update,
  delete, suspend, activate. They write to Keycloak as well as to `profile`,
  and need a running Keycloak.
- **`/api/users/me` and `/api/users/profile`** — "my profile" needs the caller's
  identity from a JWT.
- **Avatars** (`avatar` bytea, `/avatar/{username}`) these are file uploads, not CRUD.
- **Response DTOs** — `/directory` and `/teachers` currently return the full
  entity rather than the contract's shape (`displayName`, `avatarUrl`). The
  same layer makes responses role-aware.
- **`@EntityGraph`** on the repository queries instead of eager fetching.
- **Flyway migrations** in place of `data.sql`, which matches the team architecture.
