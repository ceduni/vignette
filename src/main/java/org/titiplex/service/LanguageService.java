package org.titiplex.service;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.titiplex.api.dto.LanguageDto;
import org.titiplex.api.dto.LanguageOptionDto;
import org.titiplex.api.dto.LanguageRowDto;
import org.titiplex.api.dto.UpdateLanguageRequest;
import org.titiplex.persistence.model.AccreditationPermissionType;
import org.titiplex.persistence.model.AccreditationScopeType;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.CommunityAccreditationRepository;
import org.titiplex.persistence.repo.LanguageRepository;
import org.titiplex.persistence.repo.UserRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LanguageService {

    private final LanguageRepository repo;
    private final UserRepository userRepository;
    private final CommunityAccreditationRepository accreditationRepository;

    public LanguageService(
            LanguageRepository repo,
            UserRepository userRepository,
            CommunityAccreditationRepository accreditationRepository
    ) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.accreditationRepository = accreditationRepository;
    }

    public Page<Language> listLanguages(String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("name").ascending());
        boolean hasQuery = query != null && !query.isBlank();
        // Build LIKE pattern in Java — avoids PostgreSQL "lower(bytea)" with bound params.
        String pattern = hasQuery ? "%" + query.trim().toLowerCase(Locale.ROOT) + "%" : "%";
        return repo.search(hasQuery, pattern, pageable);
    }

    public Page<LanguageDto> listLanguagesDto(int page, int size) {
        return listLanguages(null, page, size).map(this::toDto);
    }

    public Language getLanguage(String id) {
        var l = new Language();
        l.setName("Language not found");
        return repo.findById(id).orElse(l);
    }

    public LanguageDto toDto(Language l) {
        String familyName = (l.getFamily() != null) ? l.getFamily().getName() : null;
        String parentName = (l.getParent() != null) ? l.getParent().getName() : null;

        return new LanguageDto(
                l.getId(),
                l.getName(),
                l.getLevel(),
                l.getBookkeeping(),
                l.getIso639P3code(),
                l.getLatitude(),
                l.getLongitude(),
                l.getCountryIds(),
                l.getDescription(),
                l.getMarkupDescription(),
                l.getFamilyId(),
                familyName,
                l.getParentId(),
                parentName
        );
    }

    public LanguageDto getOneDto(String id) {
        Language l = repo.findWithFamilyAndParentById(id).orElseThrow();
        return toDto(l);
    }

    public long count() {
        return repo.count();
    }

    @Transactional
    public List<Language> saveAll(List<Language> languages) {
        return new ArrayList<>(repo.saveAll(languages));
    }

    public List<Language> findAllByIdIn(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repo.findAllByIdIn(ids);
    }

    public List<GlottologLanguageSnapshot> findGlottologSnapshotsByIdIn(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repo.findGlottologSnapshotsByIdIn(ids);
    }

    public boolean existsById(String id) {
        return repo.existsById(id);
    }

    public Optional<Language> findByIdExact(String id) {
        return repo.findById(id);
    }

    public Optional<Language> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return repo.findByName(name);
    }

    public List<Language> findAllFamilies() {
        return repo.findAllFamilies();
    }

    public List<Language> findAllReferencingIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repo.findAllReferencingIds(ids);
    }

    @Transactional
    public void deleteAll(Collection<Language> languages) {
        if (languages == null || languages.isEmpty()) {
            return;
        }
        repo.deleteAll(languages);
    }

    public boolean canEditLanguage(String username, String languageId) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return false;

        boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName()));
        if (isAdmin) return true;

        Language language = repo.findById(languageId).orElse(null);
        if (language == null) return false;

        Long userId = user.getId();

        boolean hasGlobalEdit = accreditationRepository.existsByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                userId,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.GLOBAL,
                null
        );
        if (hasGlobalEdit) return true;

        boolean hasDirectLanguageEdit = accreditationRepository.existsByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                userId,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.LANGUAGE,
                languageId
        );
        if (hasDirectLanguageEdit) return true;

        String familyId = language.getFamilyId();
        if (familyId != null && !familyId.isBlank()) {
            return accreditationRepository.existsByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                    userId,
                    AccreditationPermissionType.LANGUAGE_EDIT,
                    AccreditationScopeType.LANGUAGE_FAMILY,
                    familyId
            );
        }

        return false;
    }

    @Transactional
    public LanguageDto updateLanguage(String id, UpdateLanguageRequest req) {
        Language language = repo.findById(id).orElseThrow();

        if (req.name() != null) language.setName(req.name().trim());
        if (req.level() != null) language.setLevel(req.level().trim());
        if (req.bookkeeping() != null) language.setBookkeeping(req.bookkeeping());
        if (req.iso639P3code() != null) language.setIso639P3code(blankToNull(req.iso639P3code()));
        if (req.latitude() != null) language.setLatitude(req.latitude());
        if (req.longitude() != null) language.setLongitude(req.longitude());
        if (req.countryIds() != null) language.setCountryIds(blankToNull(req.countryIds()));
        if (req.familyId() != null) language.setFamilyId(blankToNull(req.familyId()));
        if (req.parentId() != null) language.setParentId(blankToNull(req.parentId()));
        if (req.description() != null) language.setDescription(blankToNull(req.description()));
        if (req.markupDescription() != null) language.setMarkupDescription(blankToNull(req.markupDescription()));

        return toDto(repo.save(language));
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Page<LanguageOptionDto> searchOptions(String q, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        boolean hasQuery = q != null && !q.isBlank();
        String pattern = hasQuery ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%" : "%";
        return repo.listOptions(hasQuery, pattern, pageable);
    }

    public List<Language> getLanguagesByFamily(String familyId) {
        assertFamilyExists(familyId);
        var stack = repo.findAllByFamilyId(familyId);
        List<Language> result = new ArrayList<>();

        while (!stack.isEmpty()) {
            var temp = stack.getFirst();
            stack.removeFirst();
            if (temp.getLevel().equalsIgnoreCase("family") || temp.getLevel().equalsIgnoreCase("parent")) {
                stack.addAll(repo.findAllByFamilyId(temp.getId()));
            } else {
                result.add(temp);
            }
        }

        return result;
    }

    public void assertFamilyExists(String familyId) {
        Language family = repo.findById(familyId)
                .orElseThrow(() -> new NoSuchElementException("Language family not found"));
        if (family.getLevel() == null || !family.getLevel().equalsIgnoreCase("family")) {
            throw new NoSuchElementException("Language family not found");
        }
    }

    public List<String> findLanguageIdsByCountryIsoA3(String isoA3) {
        String normalizedIso = toIsoA3(isoA3);
        if (normalizedIso == null) {
            throw new IllegalArgumentException("Invalid country code");
        }

        return repo.findAllWithFamilyAndParent().stream()
                .filter(language -> language.getLevel() == null
                        || !language.getLevel().equalsIgnoreCase("family"))
                .filter(language -> languageBelongsToIso(language.getCountryIds(), normalizedIso))
                .map(Language::getId)
                .toList();
    }

    private boolean languageBelongsToIso(String countryIds, String isoA3) {
        if (countryIds == null || countryIds.isBlank()) {
            return false;
        }

        for (String token : countryIds.split("[\\s,;]+")) {
            String candidate = toIsoA3(token.trim());
            if (isoA3.equals(candidate)) {
                return true;
            }
        }

        return false;
    }

    public Map<String, List<LanguageRowDto>> listLanguagesByCountryIsoA3() {
        List<Language> languages = repo.findAllWithFamilyAndParent();

        Map<String, List<LanguageRowDto>> grouped = new LinkedHashMap<>();

        for (Language language : languages) {
            String countryIds = language.getCountryIds();
            if (countryIds == null || countryIds.isBlank()) {
                continue;
            }
            if (language.getLevel() != null && language.getLevel().equalsIgnoreCase("family")) {
                continue;
            }

            LanguageRowDto row = new LanguageRowDto(
                    language.getId(),
                    language.getName(),
                    language.getLevel(),
                    language.getCountryIds(),
                    language.getFamily() != null ? language.getFamily().getName() : language.getFamilyId(),
                    language.getParent() != null ? language.getParent().getName() : language.getParentId()
            );

            for (String token : Arrays.stream(countryIds.split("[\\s,;]+"))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList()) {
                String isoA3 = toIsoA3(token);
                if (isoA3 == null) {
                    continue;
                }
                grouped.computeIfAbsent(isoA3, key -> new ArrayList<>()).add(row);
            }
        }

        grouped.replaceAll((iso, rows) -> rows.stream()
                .sorted(Comparator.comparing(LanguageRowDto::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList()));

        return grouped;
    }

    private String toIsoA3(String rawCountryCode) {
        if (rawCountryCode == null) {
            return null;
        }

        String normalized = rawCountryCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() == 3 && normalized.chars().allMatch(Character::isLetter)) {
            return normalized;
        }

        if (normalized.length() == 2 && normalized.chars().allMatch(Character::isLetter)) {
            try {
                return Locale.of("", normalized).getISO3Country().toUpperCase(Locale.ROOT);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        return null;
    }
}
