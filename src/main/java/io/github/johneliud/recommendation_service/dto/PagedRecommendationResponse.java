package io.github.johneliud.recommendation_service.dto;

import java.util.List;

public record PagedRecommendationResponse(
        List<RecommendationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}