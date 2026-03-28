package io.github.johneliud.recommendation_service.dto;

import java.util.List;

public record RecommendationResponse(
        String id,
        String title,
        List<String> genres,
        Integer releaseYear,
        String description,
        String posterUrl,
        Double averageRating,
        long relevanceScore
) {}