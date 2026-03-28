# API Testing Guide — Recommendation Service

This guide covers testing every endpoint and filter combination using **Postman** and **curl**.

Base URL: `http://localhost:8085`

---

## Postman Setup

### Environment variables

Create a Postman environment called **Neo4flix - Local** (or extend the existing one) with these variables:

| Variable | Initial Value | Description |
|----------|--------------|-------------|
| `rec_base_url` | `http://localhost:8085` | Recommendation service base URL |
| `access_token` | *(from user-microservice login)* | User JWT — required for all endpoints |

### Authorization

All endpoints require authentication. Set the **Authorization** tab:
- Type: `Bearer Token`
- Token: `{{access_token}}`

Obtain a token via `POST http://localhost:8082/api/auth/login`.

---

## Recommendations

### Get recommendations (default)

**GET** `{{rec_base_url}}/api/recommendations`

Headers: `Authorization: Bearer {{access_token}}`

Expected: `200 OK` with a paginated recommendations object.

curl:
```bash
curl -s "http://localhost:8085/api/recommendations" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

### Filter by genre

**GET** `{{rec_base_url}}/api/recommendations?genre=Action`

Returns only movies whose `genres` list contains `"Action"`.

curl:
```bash
curl -s "http://localhost:8085/api/recommendations?genre=Action" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

### Filter by release year range

**GET** `{{rec_base_url}}/api/recommendations?yearFrom=2010&yearTo=2020`

Returns only movies with `releaseYear` between 2010 and 2020 inclusive.

curl:
```bash
curl -s "http://localhost:8085/api/recommendations?yearFrom=2010&yearTo=2020" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Use `yearFrom` alone for "from year onwards":
```bash
curl -s "http://localhost:8085/api/recommendations?yearFrom=2015" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Use `yearTo` alone for "up to year":
```bash
curl -s "http://localhost:8085/api/recommendations?yearTo=2000" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

### Combine genre and year filters

**GET** `{{rec_base_url}}/api/recommendations?genre=Thriller&yearFrom=2010&yearTo=2023`

All filters are applied together — results must satisfy every active filter.

curl:
```bash
curl -s "http://localhost:8085/api/recommendations?genre=Thriller&yearFrom=2010&yearTo=2023" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

### Pagination

**GET** `{{rec_base_url}}/api/recommendations?page=0&size=5`

Fetches the first 5 recommendations. `page` is zero-based.

curl:
```bash
curl -s "http://localhost:8085/api/recommendations?page=0&size=5" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Fetch the second page:
```bash
curl -s "http://localhost:8085/api/recommendations?page=1&size=5" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

### Pagination with filters

```bash
curl -s "http://localhost:8085/api/recommendations?genre=Drama&page=0&size=10" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

## Validation errors

### Negative page

```bash
curl -s "http://localhost:8085/api/recommendations?page=-1" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Expected: `400 Bad Request`
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "page must not be negative",
  "instance": "/api/recommendations"
}
```

---

### Size exceeds maximum

```bash
curl -s "http://localhost:8085/api/recommendations?size=101" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Expected: `400 Bad Request`
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "size must be between 1 and 100",
  "instance": "/api/recommendations"
}
```

---

### Size zero

```bash
curl -s "http://localhost:8085/api/recommendations?size=0" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Expected: `400 Bad Request`
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "size must be between 1 and 100",
  "instance": "/api/recommendations"
}
```

---

## Authentication errors

### Missing token

```bash
curl -s "http://localhost:8085/api/recommendations" | jq .
```

Expected: `401 Unauthorized`

---

### Invalid token

```bash
curl -s "http://localhost:8085/api/recommendations" \
  -H "Authorization: Bearer invalid.token.here" | jq .
```

Expected: `401 Unauthorized`

---

## Empty results

If the authenticated user has no ratings with score ≥ 4, both recommendation algorithms return nothing:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

To test this, log in as a new user who has not rated any movies, or as a user whose only ratings are ≤ 3.

---

## Postman Collection (import-ready)

Build the collection by:

1. Creating a new collection named **Neo4flix - Recommendation Service**
2. Adding a folder **Recommendations**
3. Setting collection-level variable `rec_base_url` to `http://localhost:8085`
4. Setting the collection authorization to `Bearer Token` with `{{access_token}}`
5. Adding requests for each filter combination from this guide