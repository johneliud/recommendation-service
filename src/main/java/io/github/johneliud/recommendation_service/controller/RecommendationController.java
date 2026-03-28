package io.github.johneliud.recommendation_service.controller;

import io.github.johneliud.recommendation_service.dto.PagedRecommendationResponse;
import io.github.johneliud.recommendation_service.service.RecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        String userId = authentication.getName();
        log.info("GET /api/recommendations - userId={}, genre={}, yearFrom={}, yearTo={}, page={}, size={}",
                userId, genre, yearFrom, yearTo, page, size);

        PagedRecommendationResponse response =
                recommendationService.getRecommendations(userId, genre, yearFrom, yearTo, page, size);

        return ResponseEntity.ok(response);
    }
}