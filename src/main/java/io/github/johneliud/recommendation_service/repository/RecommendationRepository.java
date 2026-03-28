package io.github.johneliud.recommendation_service.repository;

import io.github.johneliud.recommendation_service.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RecommendationRepository {

    private final Neo4jClient neo4jClient;

    /**
     * Collaborative filtering: find movies rated ≥ 4 by users who share
     * highly-rated movies with the current user, excluding already-rated movies.
     */
    public Collection<RecommendationResponse> findCollaborative(String userId) {
        String query = """
                MATCH (myRating:Rating {userId: $userId})
                WHERE myRating.score >= 4
                WITH COLLECT(myRating.movieId) AS myMovies
                MATCH (otherRating:Rating)
                WHERE otherRating.movieId IN myMovies
                  AND otherRating.userId <> $userId
                  AND otherRating.score >= 4
                WITH COLLECT(DISTINCT otherRating.userId) AS similarUsers, myMovies
                MATCH (rec:Rating)
                WHERE rec.userId IN similarUsers
                  AND rec.score >= 4
                  AND NOT rec.movieId IN myMovies
                WITH rec.movieId AS movieId, COUNT(rec) AS relevance
                MATCH (m:Movie {id: movieId})
                RETURN m.id AS id, m.title AS title, m.genres AS genres,
                       m.releaseYear AS releaseYear, m.description AS description,
                       m.posterUrl AS posterUrl, m.averageRating AS averageRating,
                       relevance
                ORDER BY relevance DESC
                """;

        return neo4jClient.query(query)
                .bind(userId).to("userId")
                .fetchAs(RecommendationResponse.class)
                .mappedBy((typeSystem, record) -> mapRecord(record))
                .all();
    }

    /**
     * Content-based filtering: find movies sharing genres with movies the
     * current user rated ≥ 4, excluding already-rated movies.
     */
    public Collection<RecommendationResponse> findContentBased(String userId) {
        String query = """
                MATCH (myRating:Rating {userId: $userId})
                WHERE myRating.score >= 4
                WITH COLLECT(myRating.movieId) AS myMovies
                MATCH (rated:Movie)
                WHERE rated.id IN myMovies
                WITH COLLECT(DISTINCT rated.genres) AS allGenreLists, myMovies
                UNWIND allGenreLists AS genreList
                UNWIND genreList AS genre
                WITH COLLECT(DISTINCT genre) AS likedGenres, myMovies
                MATCH (m:Movie)
                WHERE ANY(g IN m.genres WHERE g IN likedGenres)
                  AND NOT m.id IN myMovies
                RETURN m.id AS id, m.title AS title, m.genres AS genres,
                       m.releaseYear AS releaseYear, m.description AS description,
                       m.posterUrl AS posterUrl, m.averageRating AS averageRating,
                       SIZE([g IN m.genres WHERE g IN likedGenres]) AS relevance
                ORDER BY relevance DESC
                """;

        return neo4jClient.query(query)
                .bind(userId).to("userId")
                .fetchAs(RecommendationResponse.class)
                .mappedBy((typeSystem, record) -> mapRecord(record))
                .all();
    }

    private RecommendationResponse mapRecord(org.neo4j.driver.Record record) {
        Value genres = record.get("genres");
        List<String> genreList = genres.isNull()
                ? List.of()
                : genres.asList(Value::asString);

        Value avgRating = record.get("averageRating");
        Value description = record.get("description");
        Value posterUrl = record.get("posterUrl");

        return new RecommendationResponse(
                record.get("id").asString(),
                record.get("title").asString(),
                genreList,
                record.get("releaseYear").asInt(),
                description.isNull() ? null : description.asString(),
                posterUrl.isNull() ? null : posterUrl.asString(),
                avgRating.isNull() ? null : avgRating.asDouble(),
                record.get("relevance").asLong()
        );
    }
}