package org.titiplex.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.titiplex.api.dto.AudioRowDto;
import org.titiplex.api.dto.LanguagePreviewAudioDto;
import org.titiplex.api.dto.ScenarioBackgroundAudioDto;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.AudioScope;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.titiplex.service.storage.FileStorageService;
import org.titiplex.service.storage.MediaContent;
import org.titiplex.service.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    private final AudioRepository audios;
    private final ThumbnailService thumbnailService;
    private final ScenarioService scenarioService;
    private final ScenarioRepository scenarioRepository;
    private final FileStorageService storage;

    public AudioService(
            AudioRepository audios,
            ThumbnailService thumbnailService,
            ScenarioService scenarioService,
            ScenarioRepository scenarioRepository,
            FileStorageService storage
    ) {
        this.audios = audios;
        this.thumbnailService = thumbnailService;
        this.scenarioService = scenarioService;
        this.scenarioRepository = scenarioRepository;
        this.storage = storage;
    }

    public List<AudioRowDto> listForThumbnail(Long thumbnailId) {
        return audios.findByThumbnailIdOrderByIdxAsc(thumbnailId).stream()
                .map(this::toAudioRowDto)
                .toList();
    }

    public List<AudioRowDto> listForLanguage(String languageId) {
        return audios.findAllPublishedByLanguageId(languageId).stream()
                .map(this::toAudioRowDto)
                .toList();
    }

    public List<ScenarioBackgroundAudioDto> listBackgroundForScenario(Long scenarioId) {
        Long activeAudioId = scenarioService.getRequiredScenario(scenarioId).getActiveBackgroundAudioId();
        return audios.findByScenarioIdAndScopeOrderByIdxAscIdAsc(scenarioId, AudioScope.BACKGROUND).stream()
                .map(audio -> toBackgroundDto(audio, activeAudioId))
                .toList();
    }

    public LanguagePreviewAudioDto getLanguagePreviewAudio(String languageId) {
        log.info("Preview audio requested for language={}", languageId);

        List<Scenario> scenarios = scenarioRepository.findPublishedByLanguageIdOrderByCreatedAtAscIdAsc(languageId);

        for (Scenario scenario : scenarios) {
            List<Thumbnail> thumbnails = thumbnailService.listByScenarioId(scenario.getId());

            for (Thumbnail thumbnail : thumbnails) {
                List<Audio> thumbnailAudios = audios.findByThumbnailIdOrderByIdxAsc(thumbnail.getId());
                if (thumbnailAudios.isEmpty()) {
                    continue;
                }

                Audio audio = thumbnailAudios.getFirst();
                String contentUrl = "/api/audios/" + audio.getId() + "/content";

                log.info(
                        "Preview audio selected language={} scenario={} thumbnail={} audio={} url={}",
                        languageId,
                        scenario.getId(),
                        thumbnail.getId(),
                        audio.getId(),
                        contentUrl
                );

                return new LanguagePreviewAudioDto(
                        audio.getId(),
                        audio.getTitle(),
                        audio.getMime(),
                        contentUrl,
                        scenario.getId(),
                        scenario.getTitle(),
                        thumbnail.getId(),
                        audio.getLanguageId(),
                        audio.getIdx(),
                        null
                );
            }
        }

        log.info("No preview audio found for language={}", languageId);
        throw new NoSuchElementException("No preview audio found for language " + languageId);
    }

    public Long getScenarioIdForAudio(Long audioId) {
        return getAudioOrThrow(audioId).getScenarioId();
    }

    public Audio getAudioOrThrow(Long audioId) {
        return audios.findById(audioId).orElseThrow(() -> new NoSuchElementException("Audio not found"));
    }

    @Transactional
    public Long createAudio(Long thumbnailId,
                            String title,
                            Integer idx,
                            Long authorId,
                            MultipartFile audioFile,
                            Double markerX,
                            Double markerY,
                            String markerLabel) throws Exception {

        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("audio is empty");
        }

        if (markerX != null && (markerX < 0.0 || markerX > 100.0)) {
            throw new IllegalArgumentException("markerX must be between 0 and 100");
        }
        if (markerY != null && (markerY < 0.0 || markerY > 100.0)) {
            throw new IllegalArgumentException("markerY must be between 0 and 100");
        }
        if ((markerX == null) != (markerY == null)) {
            throw new IllegalArgumentException("markerX and markerY must be both set or both empty");
        }
        String normalizedMarkerLabel = normalizeOptional(markerLabel, 120, "Marker label");

        Thumbnail t = thumbnailService.getThumbnailById(thumbnailId);
        Long scenarioId = t.getScenarioId();
        Scenario s = scenarioService.getRequiredScenario(scenarioId);

        int effectiveIdx = (idx != null) ? idx : (audios.maxIdx(thumbnailId) + 1);
        if (audios.existsByThumbnailIdAndIdx(thumbnailId, effectiveIdx)) {
            throw new IllegalArgumentException("idx already used for this thumbnail");
        }

        String effectiveTitle = resolveAudioTitle(title, audioFile.getOriginalFilename(), "Audio " + effectiveIdx);
        StoredFile stored = storage.storeAudio(audioFile, scenarioId, thumbnailId);

        Audio a = new Audio();
        a.setThumbnailId(thumbnailId);
        a.setScope(AudioScope.SCENE);
        a.setMime(stored.contentType());
        a.setAudioSha256(stored.sha256());
        a.setStoragePath(stored.relativePath());
        a.setSizeBytes(stored.sizeBytes());
        a.setOriginalFilename(stored.originalFilename());

        a.setTitle(effectiveTitle);

        a.setIdx(effectiveIdx);
        a.setMarkerX(markerX);
        a.setMarkerY(markerY);
        a.setMarkerLabel(normalizedMarkerLabel);
        a.setAuthorId(authorId);
        a.setScenarioId(scenarioId);
        a.setLanguageId(s.getLanguage_id());

        audios.save(a);
        return a.getId();
    }

    @Transactional
    public void selectBackgroundAudio(Long scenarioId, Long audioId) {
        Scenario scenario = scenarioService.getRequiredScenario(scenarioId);
        if (audioId == null) {
            scenario.setActiveBackgroundAudioId(null);
            scenarioRepository.save(scenario);
            return;
        }

        Audio audio = getAudioOrThrow(audioId);
        if (audio.getScope() != AudioScope.BACKGROUND || !scenarioId.equals(audio.getScenarioId())) {
            throw new IllegalArgumentException("Audio does not belong to this scenario's background tracks");
        }

        scenario.setActiveBackgroundAudioId(audioId);
        scenarioRepository.save(scenario);
    }

    @Transactional
    public Long createBackgroundAudio(Long scenarioId,
                                      String title,
                                      String sourceLabel,
                                      String sourceUrl,
                                      Long authorId,
                                      MultipartFile audioFile) throws Exception {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("audio is empty");
        }

        Scenario s = scenarioService.getRequiredScenario(scenarioId);
        int effectiveIdx = audios.maxBackgroundIdx(scenarioId) + 1;
        String effectiveTitle = resolveAudioTitle(
                title,
                audioFile.getOriginalFilename(),
                "Background audio " + effectiveIdx
        );
        String normalizedSourceLabel = normalizeOptional(sourceLabel, 180, "Audio credit");
        String normalizedSourceUrl = normalizeOptional(sourceUrl, 512, "Audio source URL");
        StoredFile stored = storage.storeScenarioAudio(audioFile, scenarioId);

        Audio a = new Audio();
        a.setScope(AudioScope.BACKGROUND);
        a.setThumbnailId(null);
        a.setMime(stored.contentType());
        a.setAudioSha256(stored.sha256());
        a.setStoragePath(stored.relativePath());
        a.setSizeBytes(stored.sizeBytes());
        a.setOriginalFilename(stored.originalFilename());

        a.setTitle(effectiveTitle);
        a.setIdx(effectiveIdx);
        a.setAuthorId(authorId);
        a.setScenarioId(scenarioId);
        a.setLanguageId(s.getLanguage_id());
        a.setSourceLabel(normalizedSourceLabel);
        a.setSourceUrl(normalizedSourceUrl);

        audios.save(a);
        s.setActiveBackgroundAudioId(a.getId());
        scenarioRepository.save(s);
        return a.getId();
    }

    public MediaContent loadContent(Long audioId) {
        Audio a = getAudioOrThrow(audioId);
        return storage.load(a.getStoragePath(), a.getMime(), a.getAudioSha256());
    }

    @Transactional
    public Long replaceAudio(Long audioId, String title, MultipartFile audioFile) throws Exception {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("audio is empty");
        }

        Audio audio = getAudioOrThrow(audioId);
        String effectiveTitle = resolveAudioTitle(title, audioFile.getOriginalFilename(), audio.getTitle());
        String previousPath = audio.getStoragePath();
        StoredFile stored = audio.getScope() == AudioScope.BACKGROUND
                ? storage.storeScenarioAudio(audioFile, audio.getScenarioId())
                : storage.storeAudio(audioFile, audio.getScenarioId(), audio.getThumbnailId());

        audio.setTitle(effectiveTitle);
        audio.setMime(stored.contentType());
        audio.setAudioSha256(stored.sha256());
        audio.setStoragePath(stored.relativePath());
        audio.setSizeBytes(stored.sizeBytes());
        audio.setOriginalFilename(stored.originalFilename());
        audios.save(audio);

        if (!previousPath.equals(stored.relativePath())
                && !audios.existsByStoragePathAndIdNot(previousPath, audioId)) {
            storage.deleteAfterCommit(previousPath);
        }
        return audio.getId();
    }

    @Transactional
    public void updateMarker(Long audioId, Double markerX, Double markerY, String markerLabel) {
        if (markerX != null && (markerX < 0.0 || markerX > 100.0)) {
            throw new IllegalArgumentException("markerX must be between 0 and 100");
        }
        if (markerY != null && (markerY < 0.0 || markerY > 100.0)) {
            throw new IllegalArgumentException("markerY must be between 0 and 100");
        }
        if ((markerX == null) != (markerY == null)) {
            throw new IllegalArgumentException("markerX and markerY must be both set or both empty");
        }

        Audio a = getAudioOrThrow(audioId);
        a.setMarkerX(markerX);
        a.setMarkerY(markerY);
        a.setMarkerLabel(normalizeOptional(markerLabel, 120, "Marker label"));
        audios.save(a);
    }

    @Transactional
    public void deleteAudio(Long audioId) {
        Audio a = getAudioOrThrow(audioId);
        if (a.getScope() == AudioScope.BACKGROUND) {
            Scenario scenario = scenarioService.getRequiredScenario(a.getScenarioId());
            if (audioId.equals(scenario.getActiveBackgroundAudioId())) {
                Long replacementId = audios.findByScenarioIdAndScopeOrderByIdxAscIdAsc(a.getScenarioId(), AudioScope.BACKGROUND)
                        .stream()
                        .map(Audio::getId)
                        .filter(id -> !audioId.equals(id))
                        .findFirst()
                        .orElse(null);
                scenario.setActiveBackgroundAudioId(replacementId);
                scenarioRepository.save(scenario);
            }
        }
        boolean fileIsShared = audios.existsByStoragePathAndIdNot(a.getStoragePath(), audioId);
        if (!fileIsShared) {
            storage.deleteAfterCommit(a.getStoragePath());
        }
        audios.delete(a);
    }

    private String resolveAudioTitle(String title, String originalFilename, String fallback) {
        String effectiveTitle;
        if (title != null && !title.isBlank()) {
            effectiveTitle = title.trim();
        } else if (originalFilename != null && !originalFilename.isBlank()) {
            effectiveTitle = originalFilename.trim();
        } else {
            effectiveTitle = fallback;
        }
        if (effectiveTitle.length() > 255) {
            throw new IllegalArgumentException("Audio title must be 255 characters or fewer");
        }
        return effectiveTitle;
    }

    private String normalizeOptional(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or fewer");
        }
        return normalized;
    }

    private AudioRowDto toAudioRowDto(Audio a) {
        return new AudioRowDto(
                a.getId(),
                a.getTitle(),
                a.getIdx(),
                a.getMime(),
                a.getMarkerX(),
                a.getMarkerY(),
                a.getMarkerLabel()
        );
    }

    private ScenarioBackgroundAudioDto toBackgroundDto(Audio a, Long activeAudioId) {
        return new ScenarioBackgroundAudioDto(
                a.getId(),
                a.getTitle(),
                a.getIdx(),
                a.getMime(),
                "/api/audios/" + a.getId() + "/content",
                a.getSourceLabel(),
                a.getSourceUrl(),
                Objects.equals(a.getId(), activeAudioId)
        );
    }
}
