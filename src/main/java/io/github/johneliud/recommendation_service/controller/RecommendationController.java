package io.github.johneliud.recommendation_service.controller;

import io.github.johneliud.recommendation_service.dto.PagedRecommendationResponse;
import io.github.johneliud.recommendation_service.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<PagedRecommendationResponse> getRecommendations(
            Authentication authentication,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");

        String userId = authentication.getName();
        log.info("GET /api/recommendations - userId={}, genre={}, yearFrom={}, yearTo={}, page={}, size={}",
                userId, genre, yearFrom, yearTo, page, size);

        return ResponseEntity.ok(
                recommendationService.getRecommendations(userId, genre, yearFrom, yearTo, page, size));
    }
}