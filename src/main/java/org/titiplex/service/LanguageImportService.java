package org.titiplex.service;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.api.dto.GlottologImportPreviewDto;
import org.titiplex.api.dto.GlottologSyncResultDto;
import org.titiplex.persistence.model.Language;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LanguageImportService {

    private static final String DEFAULT_CSV_RESOURCE = "glottolog/languoid.csv";
    private static final int SAVE_CHUNK_SIZE = 2000;
    private static final int LOOKUP_CHUNK_SIZE = 1000;
    private static final Logger log = LoggerFactory.getLogger(LanguageImportService.class);

    private final LanguageService languageService;

    @PersistenceContext
    private EntityManager entityManager;

    public LanguageImportService(LanguageService languageService) {
        this.languageService = languageService;
    }

    public List<Language> loadLanguagesFromCsv(Path csvPath) throws IOException {
        if (!Files.exists(csvPath)) {
            throw new IOException("File does not exist: " + csvPath);
        }
        if (!Files.isReadable(csvPath)) {
            throw new IOException("File is not readable: " + csvPath);
        }
        if (Files.size(csvPath) == 0) {
            throw new IOException("File is empty: " + csvPath);
        }

        try (InputStream inputStream = Files.newInputStream(csvPath)) {
            return loadLanguagesFromInputStream(inputStream);
        }
    }

    public List<Language> loadLanguagesFromClasspath() throws IOException {
        ClassPathResource resource = new ClassPathResource(DEFAULT_CSV_RESOURCE);
        if (!resource.exists()) {
            throw new IOException("Classpath resource not found: " + DEFAULT_CSV_RESOURCE);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return loadLanguagesFromInputStream(inputStream);
        }
    }

    private List<Language> loadLanguagesFromInputStream(InputStream inputStream) throws IOException {
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        CsvSchema schema = CsvSchema.emptySchema().withHeader();

        MappingIterator<Language> iterator = csvMapper
                .readerFor(Language.class)
                .with(schema)
                .readValues(inputStream);

        List<Language> result = iterator.readAll();
        for (Language language : result) {
            normalizeEmptyStrings(language);
            language.setName(normalizeLanguageName(language.getName()));
        }
        return result;
    }

    /**
     * Normalize Glottolog ASCII phonetic shortcuts to Unicode letters
     * (Khoisan clicks and glottal-stop apostrophes).
     */
    static String normalizeLanguageName(String name) {
        return normalizeGlottalApostrophes(normalizeClickOrthography(name));
    }

    /**
     * Glottolog CLDF often stores Khoisan clicks in ASCII digraphs
     * ({@code //}, {@code /}, {@code =/}, {@code !}). Convert them to the
     * standard Unicode click letters so names like {@code //Xegwi} display as {@code ǁXegwi}.
     */
    static String normalizeClickOrthography(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String normalized = name;
        normalized = normalized.replace("//", "\u01C1"); // ǁ lateral click
        normalized = normalized.replace("=/", "\u01C2"); // ǂ alveolar click
        normalized = normalized.replace("/=", "\u01C2"); // ǂ
        normalized = normalized.replace("/", "\u01C0");  // ǀ dental click
        normalized = normalized.replace("!", "\u01C3");  // ǃ retroflex click
        return normalized;
    }

    private static final Pattern FRENCH_ELISION_APOSTROPHE = Pattern.compile(
            "(?i)\\b([dljtncs])'(?=[AEIOUÀÂÄÆÉÈÊËÏÎÔŒÙÛÜÁÉÍÓÚỲŸ])"
    );
    private static final Pattern ENGLISH_POSSESSIVE_APOSTROPHE = Pattern.compile("(?<=[A-Za-z])'s\\b");

    /**
     * Convert ASCII apostrophes used as glottal stops ({@code 'Are'are}) to
     * saltillo ({@code ꞌAreꞌare}), while keeping English possessives and French elisions.
     */
    static String normalizeGlottalApostrophes(String name) {
        if (name == null || name.isBlank() || name.indexOf('\'') < 0) {
            return name;
        }

        List<String> holders = new ArrayList<>();
        String protectedName = FRENCH_ELISION_APOSTROPHE.matcher(name).replaceAll(match -> {
            holders.add(match.group(0));
            return "\uE000" + (holders.size() - 1) + "\uE001";
        });
        protectedName = ENGLISH_POSSESSIVE_APOSTROPHE.matcher(protectedName).replaceAll(match -> {
            holders.add(match.group(0));
            return "\uE000" + (holders.size() - 1) + "\uE001";
        });

        String normalized = protectedName.replace("'", "\uA78C"); // ꞌ
        for (int i = 0; i < holders.size(); i++) {
            normalized = normalized.replace("\uE000" + i + "\uE001", holders.get(i));
        }
        return normalized;
    }

    List<Language> selectForImport(List<Language> all) {
        Map<String, Language> byId = all.stream()
                .filter(language -> language.getId() != null && !language.getId().isBlank())
                .collect(Collectors.toMap(
                        Language::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<String> orderedIds = new LinkedHashSet<>();

        for (Language language : all) {
            if (!isLanguageOrDialect(language) || !hasCountryIds(language)) {
                continue;
            }
            orderedIds.add(language.getId());
            collectReferenceClosure(language, byId, orderedIds);
        }

        return orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public GlottologImportPreviewDto previewFromClasspath() throws IOException {
        return preview(loadLanguagesFromClasspath());
    }

    public GlottologImportPreviewDto previewFromCsv(Path csvPath) throws IOException {
        return preview(loadLanguagesFromCsv(csvPath));
    }

    private GlottologImportPreviewDto preview(List<Language> all) {
        List<Language> selected = selectForImport(all);
        return new GlottologImportPreviewDto(all.size(), selected.size(), languageService.count());
    }

    @Transactional
    public GlottologSyncResultDto syncFromClasspath() throws IOException {
        return sync(loadLanguagesFromClasspath(), GlottologUpdateProgressListener.NONE);
    }

    @Transactional
    public GlottologSyncResultDto syncFromCsv(Path csvPath) throws IOException {
        return sync(loadLanguagesFromCsv(csvPath), GlottologUpdateProgressListener.NONE);
    }

    public GlottologSyncResultDto sync(List<Language> all) {
        return sync(all, GlottologUpdateProgressListener.NONE);
    }

    @Transactional
    public GlottologSyncResultDto syncFromCsv(
            Path csvPath,
            GlottologUpdateProgressListener progressListener
    ) throws IOException {
        return sync(loadLanguagesFromCsv(csvPath), progressListener);
    }

    public GlottologSyncResultDto sync(List<Language> all, GlottologUpdateProgressListener progressListener) {
        long startedAt = System.currentTimeMillis();
        int sourceRows = all.size();
        List<Language> selected = selectForImport(all);
        long databaseCountBefore = languageService.count();
        progressListener.onSyncPrepared(sourceRows, selected.size(), databaseCountBefore);
        progressListener.onStageChanged(
                "ANALYZING_CSV",
                "Analyzing filtered CSV",
                "Comparing Glottolog rows with the existing database.",
                45,
                false
        );
        Map<String, GlottologLanguageSnapshot> existingById = loadExistingById(selected);
        Set<String> selectedIds = selected.stream().map(Language::getId).collect(Collectors.toSet());

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int processed = 0;
        List<Language> toInsert = new ArrayList<>();

        for (Language incoming : selected) {
            GlottologLanguageSnapshot existing = existingById.get(incoming.getId());
            if (existing == null) {
                if (!reserveNameForImport(incoming, selectedIds)) {
                    unchanged++;
                    processed++;
                    notifySyncProgress(progressListener, processed, selected.size(), inserted, updated, unchanged);
                    continue;
                }
                toInsert.add(copyAsNew(incoming));
                inserted++;
                processed++;
                notifySyncProgress(progressListener, processed, selected.size(), inserted, updated, unchanged);
                continue;
            }

            if (!reserveNameForImport(incoming, selectedIds)) {
                unchanged++;
                processed++;
                notifySyncProgress(progressListener, processed, selected.size(), inserted, updated, unchanged);
                continue;
            }

            if (sameGlottologData(existing, incoming)) {
                unchanged++;
                processed++;
                notifySyncProgress(progressListener, processed, selected.size(), inserted, updated, unchanged);
                continue;
            }

            Language managed = languageService.findByIdExact(incoming.getId()).orElse(null);
            if (managed == null) {
                toInsert.add(copyAsNew(incoming));
                inserted++;
                processed++;
                notifySyncProgress(progressListener, processed, selected.size(), inserted, updated, unchanged);
                continue;
            }

            applyGlottologFields(managed, incoming);
            updated++;
            processed++;
            notifySyncProgress(progressListener, processed, selected.size(), inserted, updated, unchanged);
        }

        progressListener.onStageChanged(
                "WRITING_DATABASE",
                "Final database write",
                "Applying inserts and updates to the database.",
                92,
                false
        );
        entityManager.flush();
        persistNewInChunks(toInsert);
        int deletedOrphans = deleteOrphanFamilies(selectedIds);
        if (deletedOrphans > 0) {
            log.info("Deleted {} orphan Glottolog families not present in the current import set", deletedOrphans);
        }
        progressListener.onSyncProgress(selected.size(), selected.size(), inserted, updated, unchanged);

        return new GlottologSyncResultDto(
                sourceRows,
                selected.size(),
                inserted,
                updated,
                unchanged,
                languageService.count(),
                System.currentTimeMillis() - startedAt
        );
    }

    /**
     * Supprime les familles encore en base mais absentes du CSV retenu.
     */
    private int deleteOrphanFamilies(Set<String> selectedIds) {
        List<Language> orphanFamilies = languageService.findAllFamilies().stream()
                .filter(family -> family.getId() != null && !selectedIds.contains(family.getId()))
                .toList();
        if (orphanFamilies.isEmpty()) {
            return 0;
        }

        Set<String> orphanIds = orphanFamilies.stream()
                .map(Language::getId)
                .collect(Collectors.toSet());

        // Détache les références vers ces familles (y compris entre orphelines).
        for (Language referencing : languageService.findAllReferencingIds(orphanIds)) {
            if (referencing.getFamilyId() != null && orphanIds.contains(referencing.getFamilyId())) {
                referencing.setFamilyId(null);
            }
            if (referencing.getParentId() != null && orphanIds.contains(referencing.getParentId())) {
                referencing.setParentId(null);
            }
        }
        entityManager.flush();

        languageService.deleteAll(orphanFamilies);
        entityManager.flush();
        return orphanFamilies.size();
    }

    @Transactional
    public int importIfEmpty(Path csvPath) throws IOException {
        if (languageService.count() > 0) {
            return 0;
        }
        return syncFromCsv(csvPath).inserted();
    }

    @Transactional
    public int importIfEmptyFromClasspath() throws IOException {
        if (languageService.count() > 0) {
            return 0;
        }
        return syncFromClasspath().inserted();
    }

    private Map<String, GlottologLanguageSnapshot> loadExistingById(List<Language> selected) {
        Map<String, GlottologLanguageSnapshot> existingById = new HashMap<>();
        List<String> ids = selected.stream().map(Language::getId).toList();

        for (int offset = 0; offset < ids.size(); offset += LOOKUP_CHUNK_SIZE) {
            int end = Math.min(offset + LOOKUP_CHUNK_SIZE, ids.size());
            List<String> batch = ids.subList(offset, end);
            for (GlottologLanguageSnapshot language : languageService.findGlottologSnapshotsByIdIn(batch)) {
                existingById.put(language.id(), language);
            }
        }

        return existingById;
    }

    private void persistNewInChunks(List<Language> languages) {
        for (int offset = 0; offset < languages.size(); offset += SAVE_CHUNK_SIZE) {
            int end = Math.min(offset + SAVE_CHUNK_SIZE, languages.size());
            for (Language language : languages.subList(offset, end)) {
                entityManager.persist(language);
            }
            entityManager.flush();
            entityManager.clear();
        }
    }

    private static Language copyAsNew(Language source) {
        Language language = new Language();
        language.setId(source.getId());
        applyGlottologFields(language, source);
        return language;
    }

    private static void applyGlottologFields(Language target, Language source) {
        target.setName(source.getName());
        target.setFamilyId(source.getFamilyId());
        target.setParentId(source.getParentId());
        target.setBookkeeping(source.getBookkeeping());
        target.setLevel(source.getLevel());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setIso639P3code(source.getIso639P3code());
        target.setDescription(source.getDescription());
        target.setMarkupDescription(source.getMarkupDescription());
        target.setChildFamilyCount(source.getChildFamilyCount());
        target.setChildLanguageCount(source.getChildLanguageCount());
        target.setChildDialectCount(source.getChildDialectCount());
        target.setCountryIds(source.getCountryIds());
    }

    private static boolean sameGlottologData(Language left, Language right) {
        return Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getFamilyId(), right.getFamilyId())
                && Objects.equals(left.getParentId(), right.getParentId())
                && Objects.equals(left.getBookkeeping(), right.getBookkeeping())
                && Objects.equals(left.getLevel(), right.getLevel())
                && Objects.equals(left.getLatitude(), right.getLatitude())
                && Objects.equals(left.getLongitude(), right.getLongitude())
                && Objects.equals(left.getIso639P3code(), right.getIso639P3code())
                && Objects.equals(left.getDescription(), right.getDescription())
                && Objects.equals(left.getMarkupDescription(), right.getMarkupDescription())
                && Objects.equals(left.getChildFamilyCount(), right.getChildFamilyCount())
                && Objects.equals(left.getChildLanguageCount(), right.getChildLanguageCount())
                && Objects.equals(left.getChildDialectCount(), right.getChildDialectCount())
                && Objects.equals(left.getCountryIds(), right.getCountryIds());
    }

    private static boolean sameGlottologData(GlottologLanguageSnapshot left, Language right) {
        return Objects.equals(left.name(), right.getName())
                && Objects.equals(left.familyId(), right.getFamilyId())
                && Objects.equals(left.parentId(), right.getParentId())
                && Objects.equals(left.bookkeeping(), right.getBookkeeping())
                && Objects.equals(left.level(), right.getLevel())
                && Objects.equals(left.latitude(), right.getLatitude())
                && Objects.equals(left.longitude(), right.getLongitude())
                && Objects.equals(left.iso639P3code(), right.getIso639P3code())
                && Objects.equals(left.description(), right.getDescription())
                && Objects.equals(left.markupDescription(), right.getMarkupDescription())
                && Objects.equals(left.childFamilyCount(), right.getChildFamilyCount())
                && Objects.equals(left.childLanguageCount(), right.getChildLanguageCount())
                && Objects.equals(left.childDialectCount(), right.getChildDialectCount())
                && Objects.equals(left.countryIds(), right.getCountryIds());
    }

    private boolean reserveNameForImport(Language incoming, Set<String> selectedIds) {
        Optional<Language> conflicting = languageService.findByName(incoming.getName());
        if (conflicting.isEmpty()) {
            return true;
        }

        Language holder = conflicting.get();
        if (Objects.equals(holder.getId(), incoming.getId())) {
            return true;
        }

        if (selectedIds.contains(holder.getId())) {
            log.warn(
                    "Skipping Glottolog import for id={} name='{}' because name is also assigned to selected id={}",
                    incoming.getId(),
                    incoming.getName(),
                    holder.getId()
            );
            return false;
        }

        String disambiguatedName = disambiguatedName(holder.getName(), holder.getId());
        log.info(
                "Renaming stale language id={} from '{}' to '{}' before importing id={}",
                holder.getId(),
                holder.getName(),
                disambiguatedName,
                incoming.getId()
        );
        holder.setName(disambiguatedName);
        entityManager.merge(holder);
        entityManager.flush();
        return true;
    }

    private static String disambiguatedName(String name, String id) {
        String suffix = " (" + id + ")";
        if (name != null && name.endsWith(suffix)) {
            return name;
        }
        return name + suffix;
    }

    private static void collectReferenceClosure(
            Language language,
            Map<String, Language> byId,
            Set<String> selectedIds
    ) {
        followFamilyChain(language.getFamilyId(), byId, selectedIds);
        followParentChain(language.getParentId(), byId, selectedIds);
    }

    private static void followFamilyChain(String startId, Map<String, Language> byId, Set<String> selectedIds) {
        Set<String> visited = new HashSet<>();
        String current = startId;

        while (current != null && !current.isBlank() && visited.add(current)) {
            selectedIds.add(current);
            Language node = byId.get(current);
            if (node == null) {
                break;
            }
            current = node.getFamilyId();
        }
    }

    private static void followParentChain(String startId, Map<String, Language> byId, Set<String> selectedIds) {
        Set<String> visited = new HashSet<>();
        String current = startId;

        while (current != null && !current.isBlank() && visited.add(current)) {
            selectedIds.add(current);
            Language node = byId.get(current);
            if (node == null) {
                break;
            }
            current = node.getParentId();
        }
    }

    private static boolean isLanguageOrDialect(Language language) {
        String level = normalizedLevel(language);
        return "language".equals(level) || "dialect".equals(level);
    }

    private static boolean hasCountryIds(Language language) {
        return language.getCountryIds() != null && !language.getCountryIds().isBlank();
    }

    private static String normalizedLevel(Language language) {
        if (language.getLevel() == null) {
            return "";
        }
        return language.getLevel().trim().toLowerCase(Locale.ROOT);
    }

    private static void normalizeEmptyStrings(Language language) {
        if (isBlank(language.getIso639P3code())) language.setIso639P3code(null);
        if (isBlank(language.getFamilyId())) language.setFamilyId(null);
        if (isBlank(language.getParentId())) language.setParentId(null);
        if (isBlank(language.getDescription())) language.setDescription(null);
        if (isBlank(language.getMarkupDescription())) language.setMarkupDescription(null);
        if (isBlank(language.getCountryIds())) language.setCountryIds(null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void notifySyncProgress(
            GlottologUpdateProgressListener progressListener,
            int processed,
            int total,
            int inserted,
            int updated,
            int unchanged
    ) {
        if (processed == 1 || processed == total || processed % 100 == 0) {
            progressListener.onSyncProgress(processed, total, inserted, updated, unchanged);
        }
    }
}
