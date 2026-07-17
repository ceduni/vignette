package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.titiplex.api.dto.LanguageOptionDto;
import org.titiplex.api.dto.LanguageRowDto;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.User;
import org.titiplex.service.LanguageService;
import org.titiplex.service.ScenarioService;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SuppressWarnings("SequencedCollectionMethodCanBeUsed")
@ExtendWith(MockitoExtension.class)
class LanguageApiControllerTest {

    @Mock
    private LanguageService languageService;

    @Mock
    private ScenarioService scenarioService;

    @InjectMocks
    private LanguageApiController controller;

    @Test
    void list_mapsLanguagesToRows() {
        Language family = new Language();
        family.setId("fam-mayan");
        family.setName("Mayan");

        Language parent = new Language();
        parent.setId("parent-yucatecan");
        parent.setName("Yucatecan");

        Language language = new Language();
        language.setId("chuj");
        language.setName("Chuj");
        language.setLevel("Language");
        language.setFamily(family);
        language.setParent(parent);

        Page<Language> page = new PageImpl<>(List.of(language));

        when(languageService.listLanguages("chu", 0, 10)).thenReturn(page);

        Page<LanguageRowDto> result = controller.list("chu", 0, 10);

        assertEquals(1, result.getContent().size());
        LanguageRowDto dto = result.getContent().get(0);
        assertEquals("chuj", dto.id());
        assertEquals("Chuj", dto.name());
        assertEquals("Language", dto.level());
        assertEquals("Mayan", dto.family());
        assertEquals("Yucatecan", dto.parent());
    }

    @Test
    void options_returnsServicePage() {
        Page<LanguageOptionDto> page = new PageImpl<>(List.of(
                new LanguageOptionDto("chuj", "Chuj"),
                new LanguageOptionDto("kiche", "K'iche'")
        ));

        when(languageService.searchOptions("ch", 1, 5)).thenReturn(page);

        Page<LanguageOptionDto> result = controller.options("ch", 1, 5);

        assertEquals(2, result.getContent().size());
        assertEquals("chuj", result.getContent().get(0).id());
        assertEquals("Chuj", result.getContent().get(0).name());
    }

    @Test
    void getOneScenarios_mapsLanguageScenariosToDtos() {
        User author = new User();
        author.setId(7L);
        author.setUsername("alice");

        Scenario scenario = new Scenario();
        scenario.setId(15L);
        scenario.setTitle("Story 1");
        scenario.setDescription("Intro story");
        scenario.setLanguage_id("chuj");
        scenario.setAuthor(author);
        scenario.setCreatedAt(Instant.parse("2026-03-20T10:15:30Z"));

        Language language = new Language();
        language.setId("chuj");
        language.setScenarios(Set.of(scenario));

        ScenarioDto dto = new ScenarioDto(
                15L,
                "Story 1",
                "Intro story",
                "chuj",
                "alice",
                Instant.parse("2026-03-20T10:15:30Z"),
                "DRAFT",
                null,
                null,
                null,
                null,
                List.of(),
                null,
                "NONE",
                null,
                null,
                null
        );

        when(languageService.getLanguage("chuj")).thenReturn(language);
        when(scenarioService.toDto(scenario)).thenReturn(dto);

        List<ScenarioDto> result = controller.getOneScenarios("chuj");

        assertEquals(1, result.size());
        ScenarioDto mapped = result.get(0);
        assertEquals(15L, mapped.id());
        assertEquals("Story 1", mapped.title());
        assertEquals("chuj", mapped.languageId());
        assertEquals("alice", mapped.authorUsername());
    }
}