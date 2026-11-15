package org.titiplex.service;

public record GlottologLanguageSnapshot(
        String id,
        String name,
        String familyId,
        String parentId,
        Boolean bookkeeping,
        String level,
        Float latitude,
        Float longitude,
        String iso639P3code,
        String description,
        String markupDescription,
        Integer childFamilyCount,
        Integer childLanguageCount,
        Integer childDialectCount,
        String countryIds
) {
}
