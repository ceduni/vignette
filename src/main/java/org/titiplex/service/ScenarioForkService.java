package org.titiplex.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.AudioScope;
import org.titiplex.persistence.model.ReviewStatus;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioVisibilityStatus;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.titiplex.persistence.repo.ThumbnailRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import static org.titiplex.security.AuthUtils.requireAuthenticated;

/**
 * Copies of scenarios ("forks"): creating a copy of a published scenario, with its images
 * and audios, and the original author's review of copies made by other users.
 */
@Service
public class ScenarioForkService {

    private final ScenarioRepository repo;
    private final ScenarioService scenarioService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ThumbnailRepository thumbnailRepo;
    private final AudioRepository audioRepo;

    public ScenarioForkService(
            ScenarioRepository repo,
            ScenarioService scenarioService,
            UserService userService,
            NotificationService notificationService,
            ThumbnailRepository thumbnailRepo,
            AudioRepository audioRepo
    ) {
        this.repo = repo;
        this.scenarioService = scenarioService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.thumbnailRepo = thumbnailRepo;
        this.audioRepo = audioRepo;
    }

    public Scenario forkScenario(Long originalId, String requestedTitle, Authentication authentication) {
        requireAuthenticated(authentication);

        Scenario original = scenarioService.getRequiredScenario(originalId);

        if (original.getVisibilityStatus() != ScenarioVisibilityStatus.PUBLISHED) {
            throw new IllegalArgumentException("Cannot fork an unpublished scenario");
        }

        String username = authentication.getName();
        Long userId = userService.getUserByUsername(username).getId();

        String title;
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            title = ScenarioService.validatedTitle(requestedTitle);
            if (repo.existsByTitleIgnoreCaseAndAuthorUsernameAndLanguageId(title, username, original.getLanguage_id())) {
                throw new IllegalArgumentException("Scenario already exists for this user and language");
            }
        } else {
            title = generateUniqueScenarioTitle(original.getTitle(), username, original.getLanguage_id());
        }

        Scenario fork = new Scenario();
        fork.setTitle(title);
        fork.setDescription(original.getDescription());
        fork.setLocationName(original.getLocationName());
        fork.setLatitude(original.getLatitude());
        fork.setLongitude(original.getLongitude());
        fork.setAuthor_id(userId);
        fork.setLanguage_id(original.getLanguage_id());
        fork.setCreatedAt(Instant.now());
        fork.setAuthor(userService.getUserById(userId));
        fork.setLanguage(original.getLanguage());
        fork.setVisibilityStatus(ScenarioVisibilityStatus.DRAFT);
        fork.setStoryboardLayoutMode(original.getStoryboardLayoutMode());
        fork.setStoryboardPreset(original.getStoryboardPreset());
        fork.setStoryboardColumns(original.getStoryboardColumns());
        fork.setParentScenarioId(original.getId());

        if (!userId.equals(original.getAuthor_id())) {
            fork.setReviewStatus(ReviewStatus.PENDING);
        } else {
            fork.setReviewStatus(ReviewStatus.NONE);
        }

        fork.setTags(new LinkedHashSet<>(original.getTags()));

        Scenario saved;
        try {
            saved = repo.saveAndFlush(fork);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Scenario already exists for this user and language");
        }

        // Deep copy: thumbnails + audios. Physical files are reused (content-addressed
        // storage), only new DB rows are created pointing at the same storagePath/hash.
        List<Thumbnail> originalThumbnails = thumbnailRepo.findByScenarioIdOrderByIdxAsc(original.getId());

        for (Thumbnail ot : originalThumbnails) {
            Thumbnail savedThumb = thumbnailRepo.save(copyThumbnail(ot, saved.getId(), userId));
            for (Audio oa : audioRepo.findByThumbnailIdOrderByIdxAsc(ot.getId())) {
                audioRepo.save(copyAudio(oa, saved.getId(), savedThumb.getId(), userId));
            }
        }

        // Background audios: keep the original's selected track as the fork's selected track.
        Long activeOriginalId = original.getActiveBackgroundAudioId();
        for (Audio originalAudio : audioRepo.findByScenarioIdAndScopeOrderByIdxAscIdAsc(
                original.getId(),
                AudioScope.BACKGROUND
        )) {
            Audio savedAudio = audioRepo.save(copyAudio(originalAudio, saved.getId(), null, userId));
            if (activeOriginalId != null && activeOriginalId.equals(originalAudio.getId())) {
                saved.setActiveBackgroundAudioId(savedAudio.getId());
                repo.save(saved);
            }
        }

        if (saved.getReviewStatus() == ReviewStatus.PENDING) {
            notificationService.notifyForkReviewRequested(original.getAuthor_id(), saved, original, userId);
        }

        return saved;
    }

    @Transactional
    public Scenario reviewFork(Long forkId, boolean approve, String comment, Authentication authentication) {
        requireAuthenticated(authentication);

        Scenario fork = scenarioService.getRequiredScenario(forkId);

        if (fork.getReviewStatus() != ReviewStatus.PENDING) {
            throw new IllegalStateException("Ce scénario n'est pas en attente de review.");
        }
        if (fork.getParentScenarioId() == null) {
            throw new IllegalStateException("Ce scénario n'est pas un fork.");
        }

        Scenario original = scenarioService.getRequiredScenario(fork.getParentScenarioId());
        String username = authentication.getName();
        Long reviewerId = userService.getUserByUsername(username).getId();

        if (!reviewerId.equals(original.getAuthor_id())) {
            throw new AccessDeniedException("Seul l'auteur original peut approuver ou rejeter ce fork.");
        }

        fork.setReviewStatus(approve ? ReviewStatus.APPROVED : ReviewStatus.REJECTED);
        fork.setReviewedById(reviewerId);
        fork.setReviewedAt(Instant.now());
        String normalizedComment = comment == null ? null : comment.trim();
        if (normalizedComment != null && normalizedComment.length() > 255) {
            throw new IllegalArgumentException("Review comment must be 255 characters or fewer");
        }
        fork.setReviewComment(normalizedComment == null || normalizedComment.isBlank() ? null : normalizedComment);

        Scenario saved = repo.save(fork);
        notificationService.notifyForkReviewed(saved.getAuthor_id(), saved, approve);
        return saved;
    }

    /**
     * Copies a scene image into another scenario. The physical file is shared
     * (content-addressed storage): only a new row pointing at the same file is created.
     */
    private static Thumbnail copyThumbnail(Thumbnail source, Long scenarioId, Long authorId) {
        Thumbnail copy = new Thumbnail();
        copy.setScenarioId(scenarioId);
        copy.setAuthorId(authorId);
        copy.setTitle(source.getTitle());
        copy.setIdx(source.getIdx());
        copy.setContentType(source.getContentType());
        copy.setStoragePath(source.getStoragePath());
        copy.setSizeBytes(source.getSizeBytes());
        copy.setOriginalFilename(source.getOriginalFilename());
        copy.setImageSha256(source.getImageSha256());
        copy.setGridColumn(source.getGridColumn());
        copy.setGridRow(source.getGridRow());
        copy.setGridColumnSpan(source.getGridColumnSpan());
        copy.setGridRowSpan(source.getGridRowSpan());
        copy.setImageWidth(source.getImageWidth());
        copy.setImageHeight(source.getImageHeight());
        return copy;
    }

    /**
     * Copies an audio (scene or background) into another scenario. Every content field is
     * copied, whatever the scope, so that scene and background audios cannot drift apart again.
     * Only the owner/location fields change: scenario, thumbnail (null for background) and author.
     */
    private static Audio copyAudio(Audio source, Long scenarioId, Long thumbnailId, Long authorId) {
        Audio copy = new Audio();
        copy.setScenarioId(scenarioId);
        copy.setThumbnailId(thumbnailId);
        copy.setAuthorId(authorId);
        copy.setScope(source.getScope());
        copy.setLanguageId(source.getLanguageId());
        // file
        copy.setStoragePath(source.getStoragePath());
        copy.setSizeBytes(source.getSizeBytes());
        copy.setOriginalFilename(source.getOriginalFilename());
        copy.setAudioSha256(source.getAudioSha256());
        copy.setMime(source.getMime());
        // metadata
        copy.setTitle(source.getTitle());
        copy.setIdx(source.getIdx());
        copy.setSourceLabel(source.getSourceLabel());
        copy.setSourceUrl(source.getSourceUrl());
        copy.setBackgroundVolume(source.getBackgroundVolume());
        copy.setBackgroundLoop(source.getBackgroundLoop());
        // linguistic annotations
        copy.setTranscription(source.getTranscription());
        copy.setGloss(source.getGloss());
        copy.setFreeTranslation(source.getFreeTranslation());
        // marker on the image
        copy.setMarkerX(source.getMarkerX());
        copy.setMarkerY(source.getMarkerY());
        copy.setMarkerLabel(source.getMarkerLabel());
        return copy;
    }

    private String generateUniqueScenarioTitle(String baseTitle, String username, String languageId) {
        String normalizedBase = baseTitle == null ? "Untitled scenario" : baseTitle.trim();
        String candidate = copyTitle(normalizedBase, null);
        int counter = 2;
        while (repo.existsByTitleIgnoreCaseAndAuthorUsernameAndLanguageId(candidate, username, languageId)) {
            candidate = copyTitle(normalizedBase, counter);
            counter++;
        }
        return candidate;
    }

    private String copyTitle(String baseTitle, Integer counter) {
        String suffix = counter == null ? "" : " #" + counter;
        String prefix = "Copy of ";
        int available = ScenarioService.TITLE_MAX_LENGTH - prefix.length() - suffix.length();
        String truncatedBase = baseTitle.length() <= available ? baseTitle : baseTitle.substring(0, available).trim();
        return prefix + truncatedBase + suffix;
    }
}
