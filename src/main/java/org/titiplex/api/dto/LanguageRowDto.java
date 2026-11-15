package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.titiplex.persistence.model.Language;

@Schema(name = "LanguageRowDto", description = "DTO for a single language row or description.")
public record LanguageRowDto(
        @Schema(description = "Language ID", example = "1234ung")
        String id,
        @Schema(description = "Language name", example = "English")
        String name,
        @Schema(description = "Language level", examples = {"Dialect", "Language", "Family"})
        String level,
        @Schema(description = "Country IDs where the language is present (raw backend field).", example = "FR BE CH")
        String countryIds,
        @Schema(description = "Name of the family to which the language belongs.")
        String family,
        @Schema(description = "Name of the parent language/group/family.")
        String parent,
        @Schema(description = "Latitude")
        Float latitude,
        @Schema(description = "Longitude")
        Float longitude) {

    /** Family and parent are shown by name when they are known languages, by raw id otherwise. */
    public static LanguageRowDto from(Language l) {
        return new LanguageRowDto(
                l.getId(),
                l.getName(),
                l.getLevel(),
                l.getCountryIds(),
                l.getFamily() != null ? l.getFamily().getName() : l.getFamilyId(),
                l.getParent() != null ? l.getParent().getName() : l.getParentId(),
                l.getLatitude(),
                l.getLongitude()
        );
    }
}
