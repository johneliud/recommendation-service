package io.github.johneliud.recommendation_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.johneliud.recommendation_service.dto.PagedRecommendationResponse;
import io.github.johneliud.recommendation_service.dto.RecommendationResponse;
import io.github.johneliud.recommendation_service.exception.GlobalExceptionHandler;
import io.github.johneliud.recommendation_service.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationService recommendationService;

    @InjectMocks
    private RecommendationController controller;

    private MockMvc mockMvc;

    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        new ObjectMapper();
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder) {
        var auth = new UsernamePasswordAuthenticationToken(USER_ID, null, List.of());
        return builder.principal(auth);
    }

    private PagedRecommendationResponse pagedResponse(List<RecommendationResponse> content) {
        return new PagedRecommendationResponse(content, 0, 20, content.size(),
                content.isEmpty() ? 0 : 1);
    }

    private RecommendationResponse movie(String id, String title) {
        return new RecommendationResponse(id, title, List.of("Action"), 2022, null, null, 4.5, 3);
    }

    @Test
    void getRecommendations_returnsOk_withResults() throws Exception {
        when(recommendationService.getRecommendations(eq(USER_ID), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(pagedResponse(List.of(movie("m1", "Movie 1"))));

        mockMvc.perform(withAuth(get("/api/recommendations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("m1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getRecommendations_returnsOk_withEmptyList() throws Exception {
        when(recommendationService.getRecommendations(eq(USER_ID), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(pagedResponse(List.of()));

        mockMvc.perform(withAuth(get("/api/recommendations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getRecommendations_passesGenreFilter() throws Exception {
        when(recommendationService.getRecommendations(eq(USER_ID), eq("Action"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(pagedResponse(List.of(movie("m1", "Action Movie"))));

        mockMvc.perform(withAuth(get("/api/recommendations").param("genre", "Action")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("m1"));
    }

    @Test
    void getRecommendations_passesYearRangeFilters() throws Exception {
        when(recommendationService.getRecommendations(eq(USER_ID), isNull(), eq(2020), eq(2023), eq(0), eq(20)))
                .thenReturn(pagedResponse(List.of(movie("m1", "Movie"))));

        mockMvc.perform(withAuth(get("/api/recommendations")
                        .param("yearFrom", "2020")
                        .param("yearTo", "2023")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("m1"));
    }

    @Test
    void getRecommendations_passesPaginationParams() throws Exception {
        when(recommendationService.getRecommendations(eq(USER_ID), isNull(), isNull(), isNull(), eq(2), eq(10)))
                .thenReturn(pagedResponse(List.of()));

        mockMvc.perform(withAuth(get("/api/recommendations")
                        .param("page", "2")
                        .param("size", "10")))
                .andExpect(status().isOk());
    }

    @Test
    void getRecommendations_returnsBadRequest_whenPageIsNegative() throws Exception {
        mockMvc.perform(withAuth(get("/api/recommendations").param("page", "-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRecommendations_returnsBadRequest_whenSizeExceedsMax() throws Exception {
        mockMvc.perform(withAuth(get("/api/recommendations").param("size", "101")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRecommendations_returnsBadRequest_whenSizeIsZero() throws Exception {
        mockMvc.perform(withAuth(get("/api/recommendations").param("size", "0")))
                .andExpect(status().isBadRequest());
    }
}