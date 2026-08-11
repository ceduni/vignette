package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ForkScenarioRequest", description = "Request body for forking (copying) a scenario.")
public record ForkScenarioRequest(
        @Schema(description = "Optional custom title for the copy. Falls back to an auto-generated title when omitted or blank.", example = "My remix of the original scenario")
        String title
) {
}
