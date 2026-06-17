package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.titiplex.api.dto.*;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.api.security.UserOperation;
import org.titiplex.persistence.model.Language;
import org.titiplex.service.LanguageService;
import org.titiplex.service.ScenarioService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/languages")
@Tag(name = "Language", description = "Endpoints for listing and retrieving language data.")
public class LanguageApiController {

    private final LanguageService languageService;
    private final ScenarioService scenarioService;

    public LanguageApiController(
            LanguageService languageService,
            ScenarioService scenarioService
    ) {
        this.languageService = languageService;
        this.scenarioService = scenarioService;
    }

    /**
     * Retrieves a paginated list of languages with their details.
     *
     * @param q    the search query to filter languages, default is an empty string if not provided
     * @param page the page number of the data to retrieve, default is 0 if not provided
     * @param size the number of items per page, default is 50 if not provided
     * @return a {@link Page} list of {@link LanguageRowDto} containing information about languages
     */
    @Operation(
            summary = "Lists available languages.",
            description = "Returns a paginated list of languages, including their details."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "List retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Page.class, contains = LanguageRowDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid page number or size",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping
    public Page<LanguageRowDto> list(
            @Parameter(
                    description = "Search query to filter languages",
                    schema = @Schema(type = "string", defaultValue = "\"\"")
            )
            @RequestParam(defaultValue = "") String q,

            @Parameter(
                    description = "Page number to retrieve.",
                    required = true,
                    example = "15",
                    schema = @Schema(type = "integer", minimum = "0")
            )
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(
                    description = "Size of the page to retrieve; i.e. the number of languages to retrieve for a call.",
                    required = true,
                    example = "25",
                    schema = @Schema(type = "integer", minimum = "1", defaultValue = "50")
            )
            @RequestParam(defaultValue = "50") @Min(1) int size
    ) {
        Page<Language> p = languageService.listLanguages(q, page, size);
        return p.map(l -> new LanguageRowDto(
                l.getId(),
                l.getName(),
                l.getLevel(),
                l.getCountryIds(),
                l.getFamily() != null ? l.getFamily().getName() : l.getFamilyId(),
                l.getParent() != null ? l.getParent().getName() : l.getParentId()
        ));
    }

    /**
     * Retrieves a language by its unique identifier.
     *
     * @param id ({@link String}) the unique identifier of the language to retrieve
     * @return a {@link LanguageDto} object containing detailed information about the specified language
     */
    @Operation(
            summary = "Get a language by ID.",
            description = "Returns a language object with its details based on the provided ID."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Language retrieved successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LanguageDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Language not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/{id}")
    public LanguageDto getOne(
            @Parameter(description = "ID of the language to retrieve", required = true)
            @PathVariable String id
    ) {
        return languageService.getOneDto(id);
    }

    /**
     * Retrieves a paginated list of language options based on a search query.
     *
     * @param q    the search query to filter language options, default is an empty string if not provided
     * @param page the page number of the data to retrieve, default is 0 if not provided
     * @param size the number of items per page, default is 50 if not provided
     * @return a paginated list of {@link  LanguageOptionDto} containing the language options
     */
    @Operation(
            summary = "Search for a language",
            description = """
                    Returns a paginated list of language options based on a search query.
                    Useful when needing minimal information to see, especially when querying such as in search motors.
                    """
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Language options retrieved successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Page.class, contains = LanguageOptionDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/options")
    public Page<LanguageOptionDto> options(
            @Parameter(
                    description = "The query to search for.",
                    example = "fre",
                    schema = @Schema(type = "string", defaultValue = "\"\"")
            )
            @RequestParam(defaultValue = "") String q,

            @Parameter(
                    description = "The page to return among all the results for the search.",
                    schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")
            )
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(
                    description = "The number of language per page to return.",
                    schema = @Schema(type = "integer", minimum = "1", defaultValue = "50")
            )
            @RequestParam(defaultValue = "50") @Min(1) int size
    ) {
        return languageService.searchOptions(q, page, size);
    }

    @Operation(
            summary = "List languages grouped by country (ISO_A3)",
            description = "Returns a map keyed by ISO_A3 country code, each value being a list of language rows."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Language map retrieved successfully"
            )
    })
    @GetMapping("/by-country")
    public Map<String, List<LanguageRowDto>> listByCountry() {
        return languageService.listLanguagesByCountryIsoA3();
    }

    @Operation(
            summary = "Get current user language permissions",
            description = "Returns whether the authenticated user can edit the specified language."
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Permissions resolved successfully",
                    content = @Content(schema = @Schema(implementation = LanguagePermissionsDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/{id}/permissions/me")
    public LanguagePermissionsDto myPermissions(
            @Parameter(
                    description = "ID of the language to check permissions for",
                    required = true
            )
            @PathVariable String id,

            @Parameter(hidden = true)
            Authentication auth
    ) {
        return new LanguagePermissionsDto(languageService.canEditLanguage(auth.getName(), id));
    }

    @Operation(
            summary = "Update a language",
            description = """
                    Updates editable language metadata.
                    
                    Requires either:
                    - ROLE_ADMIN
                    - or a LANGUAGE_EDIT accreditation matching:
                      - GLOBAL
                      - the specific language
                      - or the language family of the target language
                    """
    )
    @UserOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Language updated successfully",
                    content = @Content(schema = @Schema(implementation = LanguageDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not allowed to edit this language",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Language not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PutMapping("/{id}")
    public LanguageDto updateLanguage(
            @PathVariable String id,
            @RequestBody UpdateLanguageRequest request,
            Authentication auth
    ) {
        if (!languageService.canEditLanguage(auth.getName(), id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to edit this language");
        }
        return languageService.updateLanguage(id, request);
    }

    /**
     * Retrieves a list of scenarios associated with a specific language.
     *
     * @param id ({@link String}) the unique identifier of the language for which scenarios are being retrieved
     * @return a {@link List} of {@link ScenarioDto} objects containing details about the associated scenarios
     */
    @Operation(
            summary = "Retrieves scenarios associated with a language.",
            description = "Returns a list of scenarios associated with a specific language."
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Scenarios successfully retrieved.",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScenarioDto.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Language not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/{id}/scenarios")
    public List<ScenarioDto> getOneScenarios(
        @Parameter(description = "ID of the language to retrieve scenarios for", required = true)
        @PathVariable String id
    ) {
        return scenarioService.listScenariosByLanguageId(id);
     }

    @Operation(
            summary = "List languages by family",
            description = "Returns a list of languages in a specific language family."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Languages retrieved successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LanguageRowDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Family not found with the specified ID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @PublicOperation
    @GetMapping("/{id}/family")
    public List<LanguageRowDto> getFamily(
            @Parameter(description = "ID of the language family to retrieve languages for.", required = true)
            @PathVariable String id
    ) {
        return languageService.getLanguagesByFamily(id).stream()
                .map(l -> new LanguageRowDto(
                        l.getId(),
                        l.getName(),
                        l.getLevel(),
                        l.getCountryIds(),
                        l.getFamily() != null ? l.getFamily().getName() : l.getFamilyId(),
                        l.getParent() != null ? l.getParent().getName() : l.getParentId()
                )).toList();
    }
}
