package io.github.johneliud.recommendation_service.service;

import io.github.johneliud.recommendation_service.dto.PagedRecommendationResponse;
import io.github.johneliud.recommendation_service.dto.RecommendationResponse;
import io.github.johneliud.recommendation_service.repository.RecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendationRepository repository;

    @InjectMocks
    private RecommendationService service;

    private static final String USER_ID = "user-1";

    private RecommendationResponse movie(String id, String title, List<String> genres, int year, long relevance) {
        return new RecommendationResponse(id, title, genres, year, null, null, null, relevance);
    }

    @Test
    void getRecommendations_returnsMergedResults() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of(
                movie("m1", "Movie 1", List.of("Action"), 2020, 3),
                movie("m2", "Movie 2", List.of("Drama"), 2021, 2)
        ));
        when(repository.findContentBased(USER_ID)).thenReturn(List.of(
                movie("m3", "Movie 3", List.of("Comedy"), 2022, 1)
        ));

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, null, null, null, 0, 20);

        assertThat(result.content()).hasSize(3);
        assertThat(result.totalElements()).isEqualTo(3);
    }

    @Test
    void getRecommendations_sumsRelevanceForMoviesInBothApproaches() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of(
                movie("m1", "Movie 1", List.of("Action"), 2020, 3)
        ));
        when(repository.findContentBased(USER_ID)).thenReturn(List.of(
                movie("m1", "Movie 1", List.of("Action"), 2020, 2)
        ));

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, null, null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).relevanceScore()).isEqualTo(5);
    }

    @Test
    void getRecommendations_sortsDescendingByRelevance() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of(
                movie("m1", "Low", List.of("Action"), 2020, 1),
                movie("m2", "High", List.of("Action"), 2020, 5)
        ));
        when(repository.findContentBased(USER_ID)).thenReturn(List.of());

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, null, null, null, 0, 20);

        assertThat(result.content().get(0).id()).isEqualTo("m2");
        assertThat(result.content().get(1).id()).isEqualTo("m1");
    }

    @Test
    void getRecommendations_filtersOnGenre() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of(
                movie("m1", "Action Movie", List.of("Action"), 2020, 3),
                movie("m2", "Drama Movie", List.of("Drama"), 2021, 2)
        ));
        when(repository.findContentBased(USER_ID)).thenReturn(List.of());

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, "Action", null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo("m1");
    }

    @Test
    void getRecommendations_filtersOnYearFrom() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of(
                movie("m1", "Old", List.of("Action"), 2010, 2),
                movie("m2", "New", List.of("Action"), 2022, 3)
        ));
        when(repository.findContentBased(USER_ID)).thenReturn(List.of());

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, null, 2020, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo("m2");
    }

    @Test
    void getRecommendations_filtersOnYearTo() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of(
                movie("m1", "Old", List.of("Action"), 2010, 2),
                movie("m2", "New", List.of("Action"), 2022, 3)
        ));
        when(repository.findContentBased(USER_ID)).thenReturn(List.of());

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, null, null, 2015, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo("m1");
    }

    @Test
    void getRecommendations_filtersOnCombinedGenreAndYearRange() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of(
                movie("m1", "Action Old", List.of("Action"), 2010, 1),
                movie("m2", "Action New", List.of("Action"), 2022, 2),
                movie("m3", "Drama New", List.of("Drama"), 2022, 3)
        ));
        when(repository.findContentBased(USER_ID)).thenReturn(List.of());

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, "Action", 2020, 2023, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo("m2");
    }

    @Test
    void getRecommendations_paginatesCorrectly() {
        List<RecommendationResponse> movies = List.of(
                movie("m1", "A", List.of("Action"), 2020, 5),
                movie("m2", "B", List.of("Action"), 2020, 4),
                movie("m3", "C", List.of("Action"), 2020, 3),
                movie("m4", "D", List.of("Action"), 2020, 2),
                movie("m5", "E", List.of("Action"), 2020, 1)
        );
        when(repository.findCollaborative(USER_ID)).thenReturn(movies);
        when(repository.findContentBased(USER_ID)).thenReturn(List.of());

        PagedRecommendationResponse page0 = service.getRecommendations(USER_ID, null, null, null, 0, 2);
        PagedRecommendationResponse page1 = service.getRecommendations(USER_ID, null, null, null, 1, 2);

        assertThat(page0.content()).hasSize(2);
        assertThat(page0.totalElements()).isEqualTo(5);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.content().get(0).id()).isEqualTo("m1");

        assertThat(page1.content()).hasSize(2);
        assertThat(page1.content().get(0).id()).isEqualTo("m3");
    }

    @Test
    void getRecommendations_returnsEmpty_whenNoResults() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of());
        when(repository.findContentBased(USER_ID)).thenReturn(List.of());

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, null, null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
        assertThat(result.totalPages()).isEqualTo(0);
    }

    @Test
    void getRecommendations_returnsEmpty_whenFiltersMatchNothing() {
        when(repository.findCollaborative(USER_ID)).thenReturn(List.of(
                movie("m1", "Drama Movie", List.of("Drama"), 2020, 2)
        ));
        when(repository.findContentBased(USER_ID)).thenReturn(List.of());

        PagedRecommendationResponse result = service.getRecommendations(USER_ID, "Action", null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
    }
}