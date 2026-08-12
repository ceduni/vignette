package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.dto.AudioRowDto;
import org.titiplex.api.dto.LanguagePreviewAudioDto;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.AudioScope;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.titiplex.service.storage.FileStorageService;
import org.titiplex.service.storage.StoredFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("SequencedCollectionMethodCanBeUsed")
@ExtendWith(MockitoExtension.class)
class AudioServiceTest {

    @Mock
    private AudioRepository audioRepository;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private ScenarioService scenarioService;

    @Mock
    private ScenarioRepository scenarioRepository;

    @Mock
    private FileStorageService storage;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private AudioService audioService;

    @Test
    void listForThumbnail_mapsRowsInOrder() {
        Audio audio = new Audio();
        audio.setId(7L);
        audio.setTitle("Clip");
        audio.setIdx(2);
        audio.setMime("audio/webm");

        when(audioRepository.findByThumbnailIdOrderByIdxAsc(4L)).thenReturn(List.of(audio));

        List<AudioRowDto> result = audioService.listForThumbnail(4L);

        assertEquals(1, result.size());
        assertEquals("Clip", result.get(0).title());
    }

    @Test
    void createAudio_throwsWhenFileIsMissing() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> audioService.createAudio(1L, "Title", 1, 2L, null, 0.0, 0.0, "x")
        );
        assertEquals("audio is empty", ex.getMessage());
    }

    @Test
    void createAudio_throwsWhenIdxAlreadyUsed() throws Exception {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(audioRepository.existsByThumbnailIdAndIdx(4L, 5)).thenReturn(true);

        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setScenarioId(9L);

        Scenario scenario = new Scenario();
        scenario.setId(9L);
        scenario.setLanguage_id("fra");

        when(thumbnailService.getThumbnailById(4L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(9L)).thenReturn(scenario);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> audioService.createAudio(4L, "test", 5, 3L, multipartFile, 0.0, 0.0, " ")
        );

        assertEquals("idx already used for this thumbnail", ex.getMessage());
    }

    @Test
    void createAudio_savesAudioAndReturnsId() throws Exception {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(audioRepository.existsByThumbnailIdAndIdx(6L, 1)).thenReturn(false);

        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setScenarioId(10L);

        Scenario scenario = new Scenario();
        scenario.setId(10L);
        scenario.setLanguage_id("deu");

        StoredFile stored = new StoredFile(
                "audios/10/6/clip.webm",
                "abc123",
                123L,
                "audio/webm",
                "clip.webm"
        );

        when(thumbnailService.getThumbnailById(6L)).thenReturn(thumbnail);
        when(scenarioService.getRequiredScenario(10L)).thenReturn(scenario);
        when(storage.storeAudio(multipartFile, 10L, 6L)).thenReturn(stored);

        doAnswer(invocation -> {
            Audio saved = invocation.getArgument(0);
            saved.setId(88L);
            return saved;
        }).when(audioRepository).save(any(Audio.class));

        Long id = audioService.createAudio(6L, "  ", 1, 4L, multipartFile, 0.0, 0.0, " ");

        assertEquals(88L, id);

        ArgumentCaptor<Audio> captor = ArgumentCaptor.forClass(Audio.class);
        verify(audioRepository).save(captor.capture());

        Audio saved = captor.getValue();
        assertEquals(6L, saved.getThumbnailId());
        assertEquals(10L, saved.getScenarioId());
        assertEquals("deu", saved.getLanguageId());
        assertEquals("audio/webm", saved.getMime());
        assertEquals("abc123", saved.getAudioSha256());
        assertEquals("audios/10/6/clip.webm", saved.getStoragePath());
        assertEquals(123L, saved.getSizeBytes());
        assertEquals("clip.webm", saved.getOriginalFilename());
        assertEquals("Audio 1", saved.getTitle());
        assertEquals(1, saved.getIdx());
        assertEquals(0.0, saved.getMarkerX());
        assertEquals(0.0, saved.getMarkerY());
        assertNull(saved.getMarkerLabel());
    }

    @Test
    void getAudioOrThrow_returnsAudioWhenFound() {
        Audio audio = new Audio();
        audio.setId(19L);
        when(audioRepository.findById(19L)).thenReturn(Optional.of(audio));

        Audio result = audioService.getAudioOrThrow(19L);

        assertEquals(19L, result.getId());
    }

    @Test
    void replaceAudio_updatesTheExistingTakeBeforeRemovingItsOldFile() throws Exception {
        Audio audio = new Audio();
        audio.setId(19L);
        audio.setScope(AudioScope.SCENE);
        audio.setScenarioId(10L);
        audio.setThumbnailId(6L);
        audio.setTitle("Old take");
        audio.setStoragePath("audios/scenario-10/thumbnail-6/old.webm");

        StoredFile stored = new StoredFile(
                "audios/scenario-10/thumbnail-6/new.webm",
                "new-hash",
                321L,
                "audio/webm",
                "new.webm"
        );

        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("new.webm");
        when(audioRepository.findById(19L)).thenReturn(Optional.of(audio));
        when(storage.storeAudio(multipartFile, 10L, 6L)).thenReturn(stored);
        when(audioRepository.existsByStoragePathAndIdNot(audio.getStoragePath(), 19L)).thenReturn(false);

        Long id = audioService.replaceAudio(19L, "New take", multipartFile);

        assertEquals(19L, id);
        assertEquals("New take", audio.getTitle());
        assertEquals("new-hash", audio.getAudioSha256());
        assertEquals(stored.relativePath(), audio.getStoragePath());
        verify(audioRepository).save(audio);
        verify(storage).deleteAfterCommit("audios/scenario-10/thumbnail-6/old.webm");
    }

    @Test
    void deleteAudio_keepsAFileReferencedByAnotherAudio() {
        Audio audio = new Audio();
        audio.setId(19L);
        audio.setStoragePath("audios/shared.webm");
        when(audioRepository.findById(19L)).thenReturn(Optional.of(audio));
        when(audioRepository.existsByStoragePathAndIdNot("audios/shared.webm", 19L)).thenReturn(true);

        audioService.deleteAudio(19L);

        verify(storage, never()).deleteAfterCommit("audios/shared.webm");
        verify(audioRepository).delete(audio);
    }

    @Test
    void getLanguagePreviewAudio_selectsFirstPublishedScenarioThenFirstThumbnailThenFirstAudio() {
        Scenario firstScenario = new Scenario();
        firstScenario.setId(4L);
        firstScenario.setTitle("First scenario");
        firstScenario.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        firstScenario.setLanguage_id("bamb1269");

        Thumbnail firstThumbnail = new Thumbnail();
        firstThumbnail.setId(8L);
        firstThumbnail.setScenarioId(4L);
        firstThumbnail.setIdx(1);

        Audio firstAudio = new Audio();
        firstAudio.setId(15L);
        firstAudio.setTitle("Greeting clip");
        firstAudio.setMime("audio/webm");
        firstAudio.setLanguageId("bamb1269");
        firstAudio.setScenarioId(4L);
        firstAudio.setThumbnailId(8L);
        firstAudio.setIdx(1);

        when(scenarioRepository.findPublishedByLanguageIdOrderByCreatedAtAscIdAsc("bamb1269"))
                .thenReturn(List.of(firstScenario));
        when(thumbnailService.listByScenarioId(4L)).thenReturn(List.of(firstThumbnail));
        when(audioRepository.findByThumbnailIdOrderByIdxAsc(8L)).thenReturn(List.of(firstAudio));

        LanguagePreviewAudioDto result = audioService.getLanguagePreviewAudio("bamb1269");

        assertEquals(15L, result.id());
        assertEquals("Greeting clip", result.title());
        assertEquals("/api/audios/15/content", result.contentUrl());
        assertEquals(4L, result.scenarioId());
        assertEquals(8L, result.thumbnailId());
    }
}
