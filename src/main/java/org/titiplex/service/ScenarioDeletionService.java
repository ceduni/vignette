package org.titiplex.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.AudioScope;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ScenarioBookmarkRepository;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioHistoryRepository;
import org.titiplex.persistence.repo.ScenarioInviteLinkRepository;
import org.titiplex.persistence.repo.ScenarioLikeRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.titiplex.persistence.repo.ThumbnailRepository;
import org.titiplex.service.storage.FileStorageService;

/**
 * Deletes a scenario together with everything attached to it: likes, bookmarks,
 * collaborators, invite links, history, scenes with their audios, and background audios.
 * <p>
 * Media files can be shared with other scenarios (a fork reuses the files of its original),
 * so a file is only removed from storage when no other row still points at it.
 */
@Service
public class ScenarioDeletionService {

    private final ScenarioService scenarioService;
    private final ScenarioAccessService access;
    private final ScenarioRepository scenarioRepo;
    private final ScenarioLikeRepository likeRepo;
    private final ScenarioBookmarkRepository bookmarkRepo;
    private final ScenarioCollaboratorRepository collaboratorRepo;
    private final ScenarioInviteLinkRepository inviteLinkRepo;
    private final ScenarioHistoryRepository historyRepo;
    private final ThumbnailRepository thumbnailRepo;
    private final ThumbnailService thumbnailService;
    private final AudioRepository audioRepo;
    private final FileStorageService storage;

    public ScenarioDeletionService(
            ScenarioService scenarioService,
            ScenarioAccessService access,
            ScenarioRepository scenarioRepo,
            ScenarioLikeRepository likeRepo,
            ScenarioBookmarkRepository bookmarkRepo,
            ScenarioCollaboratorRepository collaboratorRepo,
            ScenarioInviteLinkRepository inviteLinkRepo,
            ScenarioHistoryRepository historyRepo,
            ThumbnailRepository thumbnailRepo,
            ThumbnailService thumbnailService,
            AudioRepository audioRepo,
            FileStorageService storage
    ) {
        this.scenarioService = scenarioService;
        this.access = access;
        this.scenarioRepo = scenarioRepo;
        this.likeRepo = likeRepo;
        this.bookmarkRepo = bookmarkRepo;
        this.collaboratorRepo = collaboratorRepo;
        this.inviteLinkRepo = inviteLinkRepo;
        this.historyRepo = historyRepo;
        this.thumbnailRepo = thumbnailRepo;
        this.thumbnailService = thumbnailService;
        this.audioRepo = audioRepo;
        this.storage = storage;
    }

    @Transactional
    public void deleteScenario(Long id, Authentication authentication) {
        Scenario scenario = scenarioService.getRequiredScenario(id);
        access.assertCanDeleteScenario(scenario, authentication);

        likeRepo.deleteByScenario_Id(id);
        bookmarkRepo.deleteByScenario_Id(id);
        collaboratorRepo.deleteByScenarioId(id);
        inviteLinkRepo.deleteByScenarioId(id);
        historyRepo.deleteByScenarioId(id);

        // Deleting a scene also deletes its audios
        for (Thumbnail thumbnail : thumbnailRepo.findByScenarioIdOrderByIdxAsc(id)) {
            thumbnailService.delete(thumbnail.getId());
        }

        for (Audio audio : audioRepo.findByScenarioIdAndScopeOrderByIdxAscIdAsc(id, AudioScope.BACKGROUND)) {
            if (!audioRepo.existsByStoragePathAndIdNot(audio.getStoragePath(), audio.getId())) {
                storage.deleteAfterCommit(audio.getStoragePath());
            }
            audioRepo.delete(audio);
        }

        scenarioRepo.delete(scenario);
    }
}
