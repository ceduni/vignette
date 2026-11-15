package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.titiplex.api.docs.BadRequestResponse;
import org.titiplex.api.dto.ApiError;
import org.titiplex.api.dto.ScenarioTagSuggestionDto;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.service.ScenarioTagService;

import java.util.List;

@RestController
@RequestMapping("/api/scenario/tags")
@Tag(name = "Scenario Tags", description = "Endpoints for managing scenario tags.")
public class ScenarioTagApiController {

    private final ScenarioTagService scenarioTagService;

    public ScenarioTagApiController(ScenarioTagService scenarioTagService) {
        this.scenarioTagService = scenarioTagService;
    }

    @GetMapping
    @Operation(
            summary = "Suggest scenario tags",
            description = """
                    Suggest (fetch or create) a scenario tag.
                    
                    Typing a new tag that does not exist will still be accepted when creating a scenario.
                    """
    )
    @PublicOperation
    @BadRequestResponse
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "List of tag suggestions",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(implementation = ScenarioTagSuggestionDto.class)
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(
                            schema = @Schema(implementation = ApiError.class)
                    )
            )
    })
    public List<ScenarioTagSuggestionDto> suggest(
            @Parameter(description = "Tag query")
            @RequestParam(defaultValue = "") String q,

            @Parameter(
                    schema = @Schema(
                            type = "integer",
                            minimum = "1",
                            maximum = "20",
                            defaultValue = "10"
                    )
            )
            @RequestParam(defaultValue = "10") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return scenarioTagService.suggest(q, safeLimit);
    }
}