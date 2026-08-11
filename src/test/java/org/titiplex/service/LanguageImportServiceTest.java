package org.titiplex.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.titiplex.api.dto.GlottologImportPreviewDto;
import org.titiplex.api.dto.GlottologSyncResultDto;
import org.titiplex.persistence.model.Language;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LanguageImportServiceTest {

    @Mock
    private LanguageService languageService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private LanguageImportService languageImportService;

    @BeforeEach
    void stubCommonCollaborators() {
        // @PersistenceContext n'est pas injecté via le constructeur : on le branche à la main.
        ReflectionTestUtils.setField(languageImportService, "entityManager", entityManager);

        // Appelés pendant sync (réserve de nom + nettoyage familles orphelines).
        lenient().when(languageService.findByName(any())).thenReturn(Optional.empty());
        lenient().when(languageService.findAllFamilies()).thenReturn(List.of());
        lenient().when(languageService.findAllReferencingIds(anyCollection())).thenReturn(List.of());
    }

    @Test
    void loadLanguagesFromCsv_throwsOnMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.csv");

        IOException ex = assertThrows(IOException.class, () -> languageImportService.loadLanguagesFromCsv(missing));

        assertTrue(ex.getMessage().contains("File does not exist"));
    }

    @Test
    void loadLanguagesFromCsv_rejectsEmptyFile(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("empty.csv");
        Files.writeString(csv, "");

        IOException ex = assertThrows(IOException.class, () -> languageImportService.loadLanguagesFromCsv(csv));

        assertTrue(ex.getMessage().contains("File is empty"));
    }

    @Test
    void loadLanguagesFromCsv_normalizesBlankFields(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("languages.csv");
        Files.writeString(csv,
                """
                        id,name,bookkeeping,level,iso639_p3code,family_id,parent_id,description,markup_description,country_ids,child_family_count,child_language_count,child_dialect_count
                        fra,French,true,language,   ,  ,  ,  ,  ,   ,0,0,0
                        """);

        List<Language> result = languageImportService.loadLanguagesFromCsv(csv);

        assertEquals(1, result.size());
        Language language = result.get(0);
        assertNull(language.getIso639P3code());
        assertNull(language.getFamilyId());
        assertNull(language.getParentId());
        assertNull(language.getDescription());
        assertNull(language.getMarkupDescription());
        assertNull(language.getCountryIds());
    }

    /**
     * Normalisation des noms : clics ASCII (//, /, =/, !) → Unicode (ǁ, ǀ, ǂ, ǃ),
     * apostrophes de coup de glotte → saltillo ꞌ, sans toucher aux possessifs anglais
     * ni aux élisions françaises.
     */
    @Test
    void normalizeLanguageName_convertsAsciiClicksAndGlottalApostrophes() {
        assertEquals("ǁXegwi", LanguageImportService.normalizeLanguageName("//Xegwi"));
        assertEquals("ǀXam", LanguageImportService.normalizeLanguageName("/Xam"));
        assertEquals("ǂUngkue", LanguageImportService.normalizeLanguageName("/=Ungkue"));
        assertEquals("Haiǁom-Akhoe", LanguageImportService.normalizeLanguageName("Hai//om-Akhoe"));
        assertEquals("ǃUi", LanguageImportService.normalizeLanguageName("!Ui"));
        assertEquals(
                "Groot Laagte ǂKxꞌauǁꞌein",
                LanguageImportService.normalizeLanguageName("Groot Laagte =/Kx'au//'ein")
        );
        assertEquals("ꞌAreꞌare", LanguageImportService.normalizeLanguageName("'Are'are"));
        assertEquals("French", LanguageImportService.normalizeLanguageName("French"));
        assertEquals("South Bird's Head", LanguageImportService.normalizeLanguageName("South Bird's Head"));
        assertEquals("Beti (Côte d'Ivoire)", LanguageImportService.normalizeLanguageName("Beti (Côte d'Ivoire)"));
        assertNull(LanguageImportService.normalizeLanguageName(null));
    }

    /** Chargement CSV : les noms sont normalisés (clics + coup de glotte) à la lecture. */
    @Test
    void loadLanguagesFromCsv_normalizesClickOrthographyInNames(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("clicks.csv");
        Files.writeString(csv,
                """
                        id,name,bookkeeping,level,iso639_p3code,family_id,parent_id,description,markup_description,country_ids,child_family_count,child_language_count,child_dialect_count
                        xegw1238,//Xegwi,false,language,xeg,,,,ZA,0,0,0
                        area1240,'Are'are,false,language,alu,,,,SB,0,0,0
                        """);

        List<Language> result = languageImportService.loadLanguagesFromCsv(csv);

        assertEquals(2, result.size());
        assertEquals("ǁXegwi", result.get(0).getName());
        assertEquals("ꞌAreꞌare", result.get(1).getName());
    }

    @Test
    void selectForImport_keepsCountryBoundLanguagesDialectsAndFamilies() throws Exception {
        Path csv = copyFixture("glottolog/sample-languoid.csv");

        List<Language> filtered = languageImportService.selectForImport(languageImportService.loadLanguagesFromCsv(csv));

        assertEquals(3, filtered.size());
        assertTrue(filtered.stream().anyMatch(language -> "fra".equals(language.getId())));
        assertTrue(filtered.stream().anyMatch(language -> "chuj-dialect".equals(language.getId())));
        assertTrue(filtered.stream().anyMatch(language -> "maya1287".equals(language.getId())));
        assertFalse(filtered.stream().anyMatch(language -> "no-country".equals(language.getId())));
        assertFalse(filtered.stream().anyMatch(language -> "orphan-family".equals(language.getId())));
    }

    @Test
    void previewFromCsv_returnsCountsWithoutSaving() throws Exception {
        Path csv = copyFixture("glottolog/sample-languoid.csv");
        when(languageService.count()).thenReturn(42L);

        GlottologImportPreviewDto preview = languageImportService.previewFromCsv(csv);

        assertEquals(5, preview.sourceRows());
        assertEquals(3, preview.selectedRows());
        assertEquals(42L, preview.databaseCount());
        verify(languageService, never()).saveAll(any());
        verify(entityManager, never()).persist(any());
    }

    /**
     * Contexte : base vide → toutes les langues retenues du CSV fixture sont insérées.
     * Fixture : src/test/resources/glottolog/sample-languoid.csv
     */
    @Test
    void sync_emptyDatabase_insertsAllSelectedLanguages() throws Exception {
        Path csv = copyFixture("glottolog/sample-languoid.csv");
        when(languageService.findGlottologSnapshotsByIdIn(any())).thenReturn(List.of());
        when(languageService.count()).thenReturn(0L, 3L);

        GlottologSyncResultDto result = languageImportService.syncFromCsv(csv);

        assertEquals(5, result.sourceRows());
        assertEquals(3, result.selectedRows());
        assertEquals(3, result.inserted());
        assertEquals(0, result.updated());
        assertEquals(0, result.unchanged());
        assertEquals(3L, result.databaseCount());

        verify(entityManager, times(3)).persist(any(Language.class));
        verify(entityManager, atLeastOnce()).flush();
    }

    /**
     * Contexte : base à moitié remplie.
     * Fixture : src/test/resources/glottolog/half-db-languoid.csv
     * En base déjà : French (nom obsolète) + Indo-European.
     * → update French, unchanged Indo-European, insert Spanish + Catalan.
     */
    @Test
    void sync_halfPopulatedDatabase_insertsUpdatesAndLeavesUnchanged() throws Exception {
        Path csv = copyFixture("glottolog/half-db-languoid.csv");
        List<Language> selected = languageImportService.selectForImport(languageImportService.loadLanguagesFromCsv(csv));
        Language indo = selected.stream().filter(l -> "indo1319".equals(l.getId())).findFirst().orElseThrow();
        Language fraFromCsv = selected.stream().filter(l -> "fra".equals(l.getId())).findFirst().orElseThrow();

        GlottologLanguageSnapshot indoSnapshot = toSnapshot(indo);
        GlottologLanguageSnapshot staleFrench = new GlottologLanguageSnapshot(
                "fra",
                "Old French",
                fraFromCsv.getFamilyId(),
                fraFromCsv.getParentId(),
                false,
                "language",
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                "FRA"
        );

        Language managedFrench = copyLanguage(fraFromCsv);
        managedFrench.setName("Old French");

        when(languageService.findGlottologSnapshotsByIdIn(any()))
                .thenReturn(List.of(staleFrench, indoSnapshot));
        when(languageService.findByIdExact("fra")).thenReturn(Optional.of(managedFrench));
        when(languageService.count()).thenReturn(2L, 4L);

        GlottologSyncResultDto result = languageImportService.syncFromCsv(csv);

        assertEquals(4, result.selectedRows());
        assertEquals(2, result.inserted(), "Spanish + Catalan manquants doivent être insérés");
        assertEquals(1, result.updated(), "French renommé doit être mis à jour");
        assertEquals(1, result.unchanged(), "Indo-European identique doit rester inchangé");
        assertEquals("French", managedFrench.getName());
        verify(entityManager, times(2)).persist(any(Language.class));
    }

    @Test
    void sync_fullDatabaseMatchingCsv_marksEverythingUnchanged() throws Exception {
        Path csv = copyFixture("glottolog/sample-languoid.csv");
        List<Language> selected = languageImportService.selectForImport(languageImportService.loadLanguagesFromCsv(csv));
        List<GlottologLanguageSnapshot> snapshots = selected.stream().map(this::toSnapshot).toList();

        when(languageService.findGlottologSnapshotsByIdIn(any())).thenReturn(snapshots);
        when(languageService.count()).thenReturn(3L);

        GlottologSyncResultDto result = languageImportService.syncFromCsv(csv);

        assertEquals(0, result.inserted());
        assertEquals(0, result.updated());
        assertEquals(3, result.unchanged());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void sync_renamesStaleNameHolderBeforeInsert() throws Exception {
        Path csv = copyFixture("glottolog/sample-languoid.csv");
        Language staleHolder = new Language();
        staleHolder.setId("old-id");
        staleHolder.setName("French");
        staleHolder.setLevel("language");

        when(languageService.findGlottologSnapshotsByIdIn(any())).thenReturn(List.of());
        when(languageService.findByName("French")).thenReturn(Optional.of(staleHolder));
        when(languageService.count()).thenReturn(1L, 3L);

        GlottologSyncResultDto result = languageImportService.syncFromCsv(csv);

        assertEquals(3, result.inserted());
        assertEquals("French (old-id)", staleHolder.getName());
        verify(entityManager, atLeastOnce()).merge(staleHolder);
    }

    @Test
    void sync_deletesOrphanFamiliesNotInSelectedSet() throws Exception {
        Path csv = copyFixture("glottolog/sample-languoid.csv");
        Language orphanFamily = new Language();
        orphanFamily.setId("orphan-family");
        orphanFamily.setName("Orphan");
        orphanFamily.setLevel("family");

        when(languageService.findGlottologSnapshotsByIdIn(any())).thenReturn(List.of());
        when(languageService.findAllFamilies()).thenReturn(List.of(orphanFamily));
        when(languageService.count()).thenReturn(0L, 3L);

        languageImportService.syncFromCsv(csv);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Language>> deleted = ArgumentCaptor.forClass(Collection.class);
        verify(languageService).deleteAll(deleted.capture());
        assertEquals(1, deleted.getValue().size());
        assertEquals("orphan-family", deleted.getValue().iterator().next().getId());
    }

    @Test
    void importIfEmpty_returnsZeroWhenDataAlreadyExists(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("languages.csv");
        Files.writeString(csv,
                """
                        id,name,bookkeeping,level,country_ids,child_family_count,child_language_count,child_dialect_count
                        fra,French,false,language,FRA,0,0,0
                        """);

        when(languageService.count()).thenReturn(12L);

        int inserted = languageImportService.importIfEmpty(csv);

        assertEquals(0, inserted);
        verify(entityManager, never()).persist(any());
    }

    @Test
    void importIfEmpty_importsFilteredLanguagesWhenRepositoryIsEmpty() throws Exception {
        Path csv = copyFixture("glottolog/sample-languoid.csv");

        when(languageService.count()).thenReturn(0L, 3L);
        when(languageService.findGlottologSnapshotsByIdIn(any())).thenReturn(List.of());

        int inserted = languageImportService.importIfEmpty(csv);

        assertEquals(3, inserted);
        verify(entityManager, times(3)).persist(any(Language.class));
    }

    private Path copyFixture(String classpathLocation) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        Path target = Files.createTempFile("glottolog-fixture-", ".csv");
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private GlottologLanguageSnapshot toSnapshot(Language language) {
        return new GlottologLanguageSnapshot(
                language.getId(),
                language.getName(),
                language.getFamilyId(),
                language.getParentId(),
                language.getBookkeeping(),
                language.getLevel(),
                language.getLatitude(),
                language.getLongitude(),
                language.getIso639P3code(),
                language.getDescription(),
                language.getMarkupDescription(),
                language.getChildFamilyCount(),
                language.getChildLanguageCount(),
                language.getChildDialectCount(),
                language.getCountryIds()
        );
    }

    private Language copyLanguage(Language source) {
        Language language = new Language();
        language.setId(source.getId());
        language.setName(source.getName());
        language.setFamilyId(source.getFamilyId());
        language.setParentId(source.getParentId());
        language.setBookkeeping(source.getBookkeeping());
        language.setLevel(source.getLevel());
        language.setLatitude(source.getLatitude());
        language.setLongitude(source.getLongitude());
        language.setIso639P3code(source.getIso639P3code());
        language.setDescription(source.getDescription());
        language.setMarkupDescription(source.getMarkupDescription());
        language.setChildFamilyCount(source.getChildFamilyCount());
        language.setChildLanguageCount(source.getChildLanguageCount());
        language.setChildDialectCount(source.getChildDialectCount());
        language.setCountryIds(source.getCountryIds());
        return language;
    }
}
