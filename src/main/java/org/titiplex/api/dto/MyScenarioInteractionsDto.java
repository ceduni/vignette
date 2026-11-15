package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO listing every scenario the authenticated user has liked and/or bookmarked.
 * Used by the frontend to hydrate its local like/bookmark state from the server
 * instead of relying solely on a client-side cache.
 *
 * @param likedScenarioIds      IDs of scenarios the user has liked
 * @param bookmarkedScenarioIds IDs of scenarios the user has bookmarked
 */
public record MyScenarioInteractionsDto(
        @Schema(description = "IDs of scenarios the authenticated user has liked")
        List<Long> likedScenarioIds,
        @Schema(description = "IDs of scenarios the authenticated user has bookmarked")
        List<Long> bookmarkedScenarioIds
) {}
