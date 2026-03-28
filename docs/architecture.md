# Architecture — Recommendation Service

## Overview

The Recommendation Service is a stateless Spring Boot service responsible for:

- Generating personalised movie recommendations for authenticated users
- Combining collaborative filtering and content-based filtering approaches
- Merging and ranking results by a calculated relevance score
- Filtering recommendations by genre and release year range
- Paginating results

It is one of five services that make up the Neo4flix backend, exposed to clients exclusively through the API Gateway.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 25 |
| Framework | Spring Boot 4.0.4 · Spring MVC (servlet stack) |
| Security | Spring Security 7 · JJWT 0.12.5 (HS256) |
| Persistence | Spring Data Neo4j · Neo4jClient (raw Cypher) · Neo4j 5+ |
| Build | Maven 3.9+ |

---

## Position in the System

```
                        ┌──────────────────────────────────────────────┐
                        │                 Neo4flix Backend             │
                        │                                              │
  Client (Angular) ───► │  API Gateway :8081                          │
                        │       │                                      │
                        │       ├──► User Microservice    :8082        │
                        │       ├──► Movie Service        :8083        │
                        │       ├──► Rating Service       :8084        │
                        │       └──► Recommendation Svc   :8085  ◄──┐ │
                        └──────────────────────────────────────────────┘
                                           │
                                       Neo4j :7687
                                  (shared neo4j database)
```

The Recommendation Service **reads directly from the shared Neo4j database** — it does not call other microservices at runtime. `Rating` and `Movie` nodes written by the rating-service and movie-service are queried directly using `Neo4jClient`.

Tokens are issued by the **user-microservice** and validated here using the shared `jwt.secret`.

---

## Package Structure

```
io.github.johneliud.recommendation_service/
│
├── config/
│   └── SecurityConfig.java              # Filter chain — all endpoints authenticated
│
├── controller/
│   └── RecommendationController.java    # GET /api/recommendations
│
├── dto/
│   ├── RecommendationResponse.java      # Movie fields + relevance score (record)
│   └── PagedRecommendationResponse.java # Paginated wrapper (record)
│
├── exception/
│   └── GlobalExceptionHandler.java      # @RestControllerAdvice → RFC 9457 ProblemDetail
│
├── repository/
│   └── RecommendationRepository.java    # Raw Cypher via Neo4jClient
│
├── security/
│   └── JwtAuthenticationFilter.java     # OncePerRequestFilter — validates Bearer tokens
│
├── service/
│   └── RecommendationService.java       # Merge, dedup, filter, sort, paginate
│
└── util/
    └── JwtUtil.java                     # Token validation and userId extraction
```

---

## Layered Architecture

```
  HTTP Request
       │
       ▼
  ┌─────────────────────────────────┐
  │  Spring Security Filter Chain   │
  │  JwtAuthenticationFilter        │  Validates Bearer token, populates SecurityContext
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  Controller Layer               │  Maps HTTP ↔ DTO, extracts userId from Authentication
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  Service Layer                  │  Merge results, apply filters, sort, paginate
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  Repository Layer               │  Neo4jClient — collaborative + content-based Cypher
  └─────────────────────────────────┘
       │
       ▼
     Neo4j
  (Rating + Movie nodes)
```

Controllers are thin — they extract `userId` from `Authentication.getName()`, validate param bounds, and delegate to the service.

---

## Recommendation Algorithms

The service uses two complementary strategies. Both run on every request; results are merged before filtering and pagination.

### Collaborative Filtering

Finds movies rated ≥ 4 by users who share highly-rated movies with the current user.

```
1. Find all movieIds rated ≥ 4 by the current user            → myMovies
2. Find other users who also rated those movies ≥ 4           → similarUsers
3. Find movies rated ≥ 4 by similarUsers                      → candidates
4. Exclude any movie already in myMovies
5. Count how many similar users rated each candidate          → relevance score
6. Return candidates ordered by relevance DESC
```

Cypher pattern (simplified):
```cypher
MATCH (myRating:Rating {userId: $userId}) WHERE myRating.score >= 4
WITH COLLECT(myRating.movieId) AS myMovies
MATCH (otherRating:Rating) WHERE otherRating.movieId IN myMovies
  AND otherRating.userId <> $userId AND otherRating.score >= 4
WITH COLLECT(DISTINCT otherRating.userId) AS similarUsers, myMovies
MATCH (rec:Rating) WHERE rec.userId IN similarUsers
  AND rec.score >= 4 AND NOT rec.movieId IN myMovies
WITH rec.movieId AS movieId, COUNT(rec) AS relevance
MATCH (m:Movie {id: movieId})
RETURN m.*, relevance ORDER BY relevance DESC
```

### Content-Based Filtering

Finds movies sharing genres with movies the current user rated ≥ 4.

```
1. Find genres from movies rated ≥ 4 by the current user      → likedGenres
2. Find all movies whose genres overlap with likedGenres
3. Exclude movies the current user has already rated
4. Score each candidate by the number of overlapping genres   → relevance score
5. Return candidates ordered by relevance DESC
```

Cypher pattern (simplified):
```cypher
MATCH (myRating:Rating {userId: $userId}) WHERE myRating.score >= 4
WITH COLLECT(myRating.movieId) AS myMovies
MATCH (rated:Movie) WHERE rated.id IN myMovies
UNWIND rated.genres AS genre
WITH COLLECT(DISTINCT genre) AS likedGenres, myMovies
MATCH (m:Movie)
WHERE ANY(g IN m.genres WHERE g IN likedGenres) AND NOT m.id IN myMovies
RETURN m.*, SIZE([g IN m.genres WHERE g IN likedGenres]) AS relevance
ORDER BY relevance DESC
```

### Result Merging

```
collaborative results  +  content-based results
          │                        │
          └──────────┬─────────────┘
                     ▼
            deduplicate by movieId
            (sum relevance scores for movies in both sets)
                     │
                     ▼
             apply genre filter
             apply yearFrom / yearTo filters
                     │
                     ▼
             sort by relevance DESC
                     │
                     ▼
             paginate (SKIP page*size, LIMIT size)
```

A movie appearing in both collaborative and content-based results gets its relevance scores summed — it ranks higher than movies found by only one approach.

---

## Security Architecture

### JWT Validation

Tokens are **issued by the user-microservice** and only **validated** here. `JwtUtil` uses the shared `jwt.secret` to verify the HS256 signature and extract the `sub` claim.

Token structure:
```
{
  "sub": "<user UUID>",
  "iat": <unix timestamp>,
  "exp": <unix timestamp>
}
```

The `sub` claim becomes the `userId` used to scope all queries.

### Filter Chain

```
Incoming request
       │
       └──► /api/recommendations/**  ──► JwtAuthenticationFilter
                                              │
                                    extract Authorization: Bearer <token>
                                              │
                                    JwtUtil.isTokenValid(token)
                                              │
                                    ┌─────────┴─────────┐
                                  valid               invalid
                                    │                   │
                            set SecurityContext    filter chain continues
                            (userId as principal) (Spring Security → 401)
                                    │
                            controller method
                            userId = authentication.getName()
```

All endpoints require authentication — there are no public routes.

---

## Neo4j Data Model

The service reads from nodes written by other services. No writes are performed.

```
┌─────────────────────────────────────┐
│           Rating (:Rating)          │
├─────────────────────────────────────┤
│ id        String (UUID)             │
│ userId    String (UUID)             │  ← used to scope queries to current user
│ movieId   String (UUID)             │  ← joined to Movie.id via property match
│ score     Integer  (1–5)            │  ← filtered: score >= 4 for high-quality signal
│ ratedAt   LocalDateTime             │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│            Movie (:Movie)           │
├─────────────────────────────────────┤
│ id            String (UUID)         │
│ title         String                │
│ genres        List<String>          │  ← used for content-based genre overlap
│ releaseYear   Integer               │  ← used for yearFrom/yearTo filters
│ description   String                │
│ posterUrl     String                │
│ averageRating Double                │
└─────────────────────────────────────┘
```

`Rating.movieId` references `Movie.id` via property matching in Cypher — there are no Neo4j relationships between the two node types in the current schema.

---

## Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to RFC 9457 `ProblemDetail` responses:

| Exception | HTTP Status |
|-----------|------------|
| `IllegalArgumentException` | 400 — invalid pagination params |
| `ConstraintViolationException` | 400 — constraint violation |
| `Exception` (catch-all) | 500 — unexpected server error |

---

## Configuration

All sensitive values are externalized via environment variables in production and via `application-secrets.properties` locally.

| Property | Env variable | Description |
|----------|-------------|-------------|
| `jwt.secret` | `JWT_SECRET` | HS256 signing key — must match user-microservice |
| `spring.neo4j.uri` | `NEO4J_URI` | Neo4j Bolt URI (e.g. `bolt://localhost:7687`) |
| `spring.neo4j.authentication.username` | `NEO4J_USERNAME` | Neo4j username |
| `spring.neo4j.authentication.password` | `NEO4J_PASSWORD` | Neo4j password |
| `spring.data.neo4j.database` | `NEO4J_DATABASE` | Database name (default `neo4j`) |
| `server.port` | `SERVER_PORT` | Default 8085 |