package io.github.johneliud.recommendation_service.service;

import io.github.johneliud.recommendation_service.dto.PagedRecommendationResponse;
import io.github.johneliud.recommendation_service.dto.RecommendationResponse;
import io.github.johneliud.recommendation_service.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository repository;

    public PagedRecommendationResponse getRecommendations(
            String userId,
            String genre,
            Integer yearFrom,
            Integer yearTo,
            int page,
            int size) {

        log.debug("Fetching recommendations for user={}, genre={}, yearFrom={}, yearTo={}, page={}, size={}",
                userId, genre, yearFrom, yearTo, page, size);

        Collection<RecommendationResponse> collaborative = repository.findCollaborative(userId);
        Collection<RecommendationResponse> contentBased = repository.findContentBased(userId);

        // Merge: sum relevance scores for movies appearing in both approaches
        Map<String, RecommendationResponse> merged = new LinkedHashMap<>();

        Stream.concat(collaborative.stream(), contentBased.stream()).forEach(r -> {
            merged.merge(r.id(), r, (existing, incoming) ->
                    new RecommendationResponse(
                            existing.id(),
                            existing.title(),
                            existing.genres(),
                            existing.releaseYear(),
                            existing.description(),
                            existing.posterUrl(),
                            existing.averageRating(),
                            existing.relevanceScore() + incoming.relevanceScore()
                    )
            );
        });

        // Apply filters and sort by combined relevance descending
        List<RecommendationResponse> filtered = merged.values().stream()
                .filter(r -> genre == null || genre.isBlank() || r.genres().contains(genre))
                .filter(r -> yearFrom == null || r.releaseYear() >= yearFrom)
                .filter(r -> yearTo == null || r.releaseYear() <= yearTo)
                .sorted(Comparator.comparingLong(RecommendationResponse::relevanceScore).reversed())
                .toList();

        long totalElements = filtered.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<RecommendationResponse> content = filtered.subList(fromIndex, toIndex);

        return new PagedRecommendationResponse(content, page, size, totalElements, totalPages);
    }
}