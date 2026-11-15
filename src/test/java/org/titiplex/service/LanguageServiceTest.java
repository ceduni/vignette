package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.titiplex.api.dto.LanguageDto;
import org.titiplex.api.dto.LanguageOptionDto;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.repo.LanguageRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageServiceTest {

    @Mock
    private LanguageRepository languageRepository;

    @InjectMocks
    private LanguageService languageService;

    @Test
    void listLanguages_sanitizesPagingBounds() {
        Page<Language> page = new PageImpl<>(List.of(new Language()));
        when(languageRepository.search(eq(false), anyString(), any(Pageable.class))).thenReturn(page);

        Page<Language> result = languageService.listLanguages("", -4, 500);

        assertEquals(1, result.getTotalElements());
        verify(languageRepository).search(eq(false), eq("%"), any(Pageable.class));
    }

    @Test
    void getLanguage_returnsFallbackWhenUnknown() {
        when(languageRepository.findById("xyz")).thenReturn(Optional.empty());

        Language language = languageService.getLanguage("xyz");

        assertEquals("Language not found", language.getName());
    }

    @Test
    void toDto_mapsFamilyAndParentNames() {
        Language language = new Language();
        language.setId("fra");
        language.setName("French");
        language.setLevel("language");

        Language family = new Language();
        family.setName("Italic");
        Language parent = new Language();
        parent.setName("Latin");

        language.setFamily(family);
        language.setParent(parent);

        LanguageDto dto = languageService.toDto(language);

        assertEquals("fra", dto.id());
        assertEquals("French", dto.name());
        assertEquals("Italic", dto.familyName());
        assertEquals("Latin", dto.parentName());
    }

    @Test
    void searchOptions_sanitizesPagingAndDelegatesToRepository() {
        Page<LanguageOptionDto> options = new PageImpl<>(List.of(new LanguageOptionDto("fra", "French")));
        when(languageRepository.listOptions(anyBoolean(), anyString(), any(Pageable.class))).thenReturn(options);

        Page<LanguageOptionDto> result = languageService.searchOptions("fr", -1, 999);

        assertEquals(1, result.getTotalElements());
        verify(languageRepository).listOptions(eq(true), eq("%fr%"), any(Pageable.class));
    }
}