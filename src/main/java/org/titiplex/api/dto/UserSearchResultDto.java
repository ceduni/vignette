package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserSearchResult", description = "Minimal user info for search/invite pickers")
public record UserSearchResultDto(
        @Schema(description = "User ID") Long id,
        @Schema(description = "Username") String username,
        @Schema(description = "Display name") String displayName
) {
}