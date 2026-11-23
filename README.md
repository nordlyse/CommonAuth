# CommonAuth

CommonAuth lets different applications use the same token.

After a sign-in, that token can still change. A value can be added, a value can be removed, or an existing value can be changed. Those updates stay on the same token, so each application can keep using it.

The browser uses one host, `common.localhost`, the same way core-app keeps its services on one Traefik entry. Keycloak, the sign-in page, and each system are paths on that host. A Java service exchanges the sign-in token for a token aimed at the system you open.

## Stack

- **Traefik 3.0** routes every request on `common.localhost`.
- **Keycloak 26.4.2** holds the realm `common` under `/auth`.
- **token-exchange** in `apps/token-exchange` is the sign-in page and the Java service that swaps tokens.
- **Sharkord**, **CRM**, **ClearFlask**, **Alexandrie**, and **Mentingo** are the systems behind that host.

## Addresses

Open [http://common.localhost](http://common.localhost). Sign in as `demo` / `demo` or `admin` / `admin`.

| Path | System |
| --- | --- |
| `/` | Sign-in page |
| `/auth` | Keycloak. Admin `admin` / `admin` |
| `/sharkord/` | Sharkord |
| `/crm/` | Comp AI CRM |
| `/clearflask/` | ClearFlask |
| `/alexandrie/` | Alexandrie |
| `/mentingo/` | Mentingo |

After sign-in the page lists those systems. Choosing one keeps you on the same page and loads that system in the frame. Traefik asks the Java service for a bearer token on each of those paths. The service exchanges the sign-in token for that system's audience and returns `Authorization`.

Sharkord, CRM, ClearFlask, and Mentingo open with the account that is already signed in, so the frame does not ask for another password. The exchanged token is attached to their requests from this host.

A signed-in caller can ask the Java service to call another system with the exchanged token:

```http
POST /exchange/forward
Content-Type: application/json

{"audience":"mentingo","method":"GET","path":"/health"}
```

The path has to start with `/` and stay inside that system.

## Run

Port `80` must be free. Another stack on `app.localhost` can keep running; this one uses `common.localhost`.

```bash
docker compose up --build
```

The first run builds the token exchange service, CRM, and Mentingo. Mentingo compiles from its upstream repository and takes a long time.

Sharkord also publishes `40000/tcp` and `40000/udp` for voice.

## Licences

The five applications are MIT or Apache-2.0. Keycloak, Traefik, Spring Boot, Maven, RustFS, MailHog, Bun, and LocalStack are MIT or Apache-2.0.

These pieces are not MIT or Apache-2.0. They stay because the selected systems do not run without them:

| Piece | Licence | Why it is here |
| --- | --- | --- |
| Eclipse Temurin | GPL-2.0 with the Classpath Exception | Java runtime for Keycloak and the token service |
| PostgreSQL 17 | PostgreSQL License | Keycloak and CRM |
| PostgreSQL 17 with pgvector | PostgreSQL License | Mentingo. The vector extension is part of that image |
| Redis 7.2 | BSD-3-Clause | Mentingo |
| nginx | BSD-2-Clause | Mentingo web image |
| MySQL 8.0 | GPL-2.0 | Alexandrie. Its server only opens a MySQL connection |
| MariaDB 10.5 | GPL-2.0 | ClearFlask search. DynamoDB and S3 run on LocalStack |

## Repository

- Page: https://github.com/nordlyse/CommonAuth
- Clone: `git@github.com:nordlyse/CommonAuth.git`
