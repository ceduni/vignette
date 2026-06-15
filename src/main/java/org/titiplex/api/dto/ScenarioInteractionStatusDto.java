package org.titiplex.api.dto;

/**
 * DTO returned by all like/bookmark endpoints.
 *
 * @param liked      whether the current user has liked this scenario
 * @param bookmarked whether the current user has bookmarked this scenario
 * @param likeCount  total number of likes (optional — 0 if not tracked)
 */
public record ScenarioInteractionStatusDto(
        boolean liked,
        boolean bookmarked,
        long likeCount
) {}
