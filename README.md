# Social Network API

A small in-memory "who knows whom" service. People are nodes, `knows` relationships are
undirected edges, and a BFS finds the shortest chain of introductions (max 3 hops).

**Stack:** Java 17+, [Javalin 6](https://javalin.io) (Jetty), Jackson, Maven, JUnit 5.
All data lives in memory (`ConcurrentHashMap` + `AtomicLong`) and is lost on restart.

## Project layout

```
social-network/
├── pom.xml
├── README.md
└── src
    ├── main/java/com/example/network
    │   ├── Main.java                         # entry point (PORT env var, default 7070)
    │   ├── controller/
    │   │   ├── ApiServer.java                # Javalin setup, error mapping, UTF-8 JSON writer
    │   │   └── PersonController.java         # routes + request validation
    │   ├── service/SocialGraphService.java   # business rules + BFS
    │   ├── repository/
    │   │   ├── GraphRepository.java          # storage interface
    │   │   └── InMemoryGraphRepository.java  # thread-safe implementation
    │   ├── model/{Person,PathResponse}.java
    │   └── exception/{ApiException,BadRequestException,PersonNotFoundException}.java
    └── test/java/com/example/network
        ├── ApiIntegrationTest.java           # real HTTP against a random port
        └── SocialGraphServiceTest.java       # unit + concurrency tests
```

## Setup

Install the following before starting:

- JDK 17 or newer
- Maven 3.8 or newer
- `curl` (optional, for the API examples)

Verify that Java and Maven are available:

```bash
java -version
mvn -version
```

From the directory containing `pom.xml`, Maven downloads the project dependencies
automatically on the first build. No database or other external service is required.

## Run

```bash
mvn compile exec:java            # starts on http://localhost:7070
PORT=8080 mvn compile exec:java  # custom port

# or build a runnable fat jar
mvn clean package
java -jar target/social-network-1.0.0.jar
```

On Windows PowerShell, set a custom port for the current terminal session with:

```powershell
$env:PORT = "8080"
mvn compile exec:java
```

The server remains attached to the terminal. Stop it with `Ctrl+C`. To confirm that
the service is running, create a person from another terminal:

```bash
curl -i -X POST http://localhost:7070/people \
  -H "Content-Type: application/json" \
  -d '{"name":"Asha"}'
```

## Test

```bash
mvn test
```

Key test: `path_threeHops_isConnected` (A-B-C-D → connected, 3 hops) and
`path_fourHops_isNotConnected` (A-B-C-D-E → `{"connected": false}`), mirrored at the
service level in `boundary_threeHopsFound_fourHopsNot`.

## API

| Method & path | Success | Errors |
|---|---|---|
| `POST /people` `{"name":"Asha"}` | `201` `{"id":1,"name":"Asha"}` | `400` bad JSON / missing, non-string or blank name |
| `POST /knows` `{"a":1,"b":2}` | `204` (idempotent, mutual) | `400` `a == b` or bad body, `404` unknown id |
| `GET /people/{id}/contacts` | `200` `[{"id":2,"name":"Ravi"}]` (sorted by id) | `400` non-numeric id, `404` `{"error":"no person X"}` |
| `GET /path?from=1&to=5` | `200` `{"connected":true,"hops":2,"path":[...]}` or `{"connected":false}` | `400` missing/non-numeric param, `404` unknown id |

Notes:
- Paths of more than 3 hops, or no path at all, return `200 {"connected": false}`.
- `from == to` for an existing person returns `hops: 0` and a one-element path.
- Names are trimmed, may contain any Unicode (e.g. `आशा`), and are limited to 200 characters.
- All error bodies have the shape `{"error": "..."}`.

### Example

```bash
curl -s -XPOST localhost:7070/people -H 'Content-Type: application/json' -d '{"name":"आशा"}'
curl -s -XPOST localhost:7070/people -H 'Content-Type: application/json' -d '{"name":"Ravi"}'
curl -s -XPOST localhost:7070/knows  -H 'Content-Type: application/json' -d '{"a":1,"b":2}' -i
curl -s 'localhost:7070/path?from=1&to=2'
```

## Design notes

- **Layering:** controller (HTTP/validation) → service (rules, BFS) → repository (storage),
  so swapping the in-memory store for a database only touches `GraphRepository`.
- **BFS:** nodes at depth 3 are never expanded, so work is bounded by the hop limit rather
  than the graph size. Neighbours are visited in id order, so results are deterministic.
- **Concurrency:** ids come from an `AtomicLong`; adjacency sets are concurrent sets, which
  makes duplicate `knows` calls harmless. People are never deleted, so existence checks
  cannot go stale.
- **Validation order:** `a == b` (400) is checked before existence (404).

## Future improvements

- Persistence (PostgreSQL / Neo4j) behind `GraphRepository`.
- Bidirectional BFS, or cached/precomputed neighbourhoods, for very large graphs.
- Pagination for `/contacts`; configurable hop limit via query param or config.
- `DELETE` endpoints (remove person / relationship).
- Auth, rate limiting, request-size limits.
- OpenAPI spec, metrics (Micrometer) and health endpoint, Dockerfile, CI pipeline.
- Duplicate-name policy / richer person profile.

## AI Disclosure
- Github Copilot
