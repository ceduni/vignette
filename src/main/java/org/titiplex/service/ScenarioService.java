package org.titiplex.service;

import org.springframework.data.domain.PageRequest;
import org.titiplex.api.dto.ScenarioLocationDto;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.api.dto.UpdateScenarioMetadataRequest;
import org.titiplex.api.dto.UpdateScenarioStoryboardRequest;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioCollaborator;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.ScenarioVisibilityStatus;
import org.titiplex.persistence.model.StoryboardLayoutMode;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.AudioScope;
import org.titiplex.persistence.model.ReviewStatus;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ScenarioBookmarkRepository;
import org.titiplex.persistence.repo.ScenarioHistoryRepository;
import org.titiplex.persistence.repo.ScenarioInviteLinkRepository;
import org.titiplex.persistence.repo.ScenarioLikeRepository;
import org.titiplex.persistence.repo.ThumbnailRepository;
import org.titiplex.service.storage.FileStorageService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.titiplex.security.AuthUtils.*;

@Service
public class ScenarioService {

    public static final int DISCOVERY_SCENARIO_LIMIT = 15;
    static final int TITLE_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final ScenarioRepository repo;
    private final UserService userService;
    private final LanguageService languageService;
    private final ScenarioTagService scenarioTagService;
    private final NotificationService notificationService;
    private final ThumbnailRepository thumbnailRepo;
    private final AudioRepository audioRepo;
    private final ScenarioCollaboratorRepository collaboratorRepo;
    private final ScenarioHistoryService scenarioHistoryService;
    private final ScenarioLikeRepository likeRepo;
    private final ScenarioBookmarkRepository bookmarkRepo;
    private final ScenarioInviteLinkRepository inviteLinkRepo;
    private final ScenarioHistoryRepository historyRepo;
    private final ThumbnailService thumbnailService;
    private final FileStorageService storage;
    private final ScenarioAccessService access;

    public ScenarioService(
            ScenarioRepository scenarioRepository,
            UserService userService,
            LanguageService languageService,
            ScenarioTagService scenarioTagService,
            NotificationService notificationService,
            ThumbnailRepository thumbnailRepo,
            AudioRepository audioRepo,
            ScenarioCollaboratorRepository collaboratorRepo,
            ScenarioHistoryService scenarioHistoryService,
            ScenarioLikeRepository likeRepo,
            ScenarioBookmarkRepository bookmarkRepo,
            ScenarioInviteLinkRepository inviteLinkRepo,
            ScenarioHistoryRepository historyRepo,
            ThumbnailService thumbnailService,
            FileStorageService storage,
            ScenarioAccessService access
    ) {
        this.repo = scenarioRepository;
        this.userService = userService;
        this.languageService = languageService;
        this.scenarioTagService = scenarioTagService;
        this.notificationService = notificationService;
        this.thumbnailRepo = thumbnailRepo;
        this.audioRepo = audioRepo;
        this.collaboratorRepo = collaboratorRepo;
        this.scenarioHistoryService = scenarioHistoryService;
        this.likeRepo = likeRepo;
        this.bookmarkRepo = bookmarkRepo;
        this.inviteLinkRepo = inviteLinkRepo;
        this.historyRepo = historyRepo;
        this.thumbnailService = thumbnailService;
        this.storage = storage;
        this.access = access;
    }

    public Scenario createScenario(String title, String description, Long authorId, String languageId, List<String> tags) {
        String normalizedTitle = validatedTitle(title);
        String normalizedDescription = validatedDescription(description);
        User author = userService.getUserById(authorId);
        if (repo.existsByTitleIgnoreCaseAndAuthorUsernameAndLanguageId(normalizedTitle, author.getUsername(), languageId)) {
            throw new IllegalArgumentException("Scenario already exists for this user and language");
        }

        Scenario scenario = new Scenario();
        scenario.setTitle(normalizedTitle);
        scenario.setDescription(normalizedDescription);
        scenario.setAuthor_id(authorId);
        scenario.setLanguage_id(languageId);
        scenario.setCreatedAt(Instant.now());
        scenario.setAuthor(author);
        scenario.setLanguage(languageService.getLanguage(languageId));
        scenario.setVisibilityStatus(ScenarioVisibilityStatus.DRAFT);
        scenario.setStoryboardLayoutMode(StoryboardLayoutMode.PRESET);
        scenario.setStoryboardPreset("GRID_3");
        scenario.setStoryboardColumns(3);
        scenario.setTags(new LinkedHashSet<>(scenarioTagService.resolveTags(tags)));
        Scenario saved = repo.save(scenario);
        scenarioHistoryService.record(saved.getId(), authorId, ScenarioHistoryAction.SCENARIO_CREATED, "Created the scenario");
        return saved;
    }

    public Scenario getRequiredScenario(Long id) {
        return repo.findByIdWithTags(id)
                .orElseThrow(() -> new NoSuchElementException("Scenario not found"));
    }

    public Scenario getVisibleScenario(Long id, Authentication authentication) {
        Scenario scenario = getRequiredScenario(id);
        access.assertCanViewScenario(scenario, authentication);
        return scenario;
    }

    public List<Scenario> listVisibleScenarios(Authentication authentication) {
        if (isAdmin(authentication)) {
            return repo.findAllWithTagsOrderByCreatedAtDesc();
        }

        String username = authenticatedUsername(authentication);
        if (username == null) {
            return repo.findAllByVisibilityStatusWithTagsOrderByCreatedAtDesc(ScenarioVisibilityStatus.PUBLISHED);
        }

        return repo.findVisibleToUsernameWithTags(username);
    }

    public List<Scenario> listMyScenarios(Authentication authentication) {
        requireAuthenticated(authentication);

        return repo.findAllByAuthorUsernameWithTagsOrderByCreatedAtDesc(authentication.getName());
    }

    public List<Scenario> listSharedWithMeScenarios(Authentication authentication) {
        requireAuthenticated(authentication);

        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        List<Long> scenarioIds = collaboratorRepo.findByUserIdAndStatus(userId, CollaborationStatus.ACCEPTED)
                .stream()
                .map(org.titiplex.persistence.model.ScenarioCollaborator::getScenarioId)
                .toList();

        if (scenarioIds.isEmpty()) {
            return List.of();
        }

        return repo.findAllByIdInWithTags(scenarioIds);
    }

    public List<ScenarioDto> listSharedWithMeScenarioDtos(Authentication authentication) {
        String username = authenticatedUsername(authentication);
        return listSharedWithMeScenarios(authentication).stream()
                .map(s -> toDto(s, username))
                .toList();
    }

    /**
     * Published scenarios a user has worked on — either as author or as an accepted
     * collaborator. Drafts are always excluded, regardless of the viewer.
     */
    public List<ScenarioDto> listPublishedScenariosWorkedOnByUsername(String username) {
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        List<Long> collaboratedIds = collaboratorRepo.findByUserIdAndStatus(user.getId(), CollaborationStatus.ACCEPTED)
                .stream()
                .map(ScenarioCollaborator::getScenarioId)
                .toList();

        Map<Long, Scenario> published = new LinkedHashMap<>();
        for (Scenario s : repo.findAllByAuthorUsernameWithTagsOrderByCreatedAtDesc(username)) {
            if (s.getVisibilityStatus() == ScenarioVisibilityStatus.PUBLISHED) {
                published.put(s.getId(), s);
            }
        }
        if (!collaboratedIds.isEmpty()) {
            for (Scenario s : repo.findAllByIdInWithTags(collaboratedIds)) {
                if (s.getVisibilityStatus() == ScenarioVisibilityStatus.PUBLISHED) {
                    published.putIfAbsent(s.getId(), s);
                }
            }
        }

        return published.values().stream()
                .sorted(Comparator.comparing(Scenario::getCreatedAt).reversed())
                .map(s -> toDto(s, null))
                .toList();
    }

    public List<Scenario> listAllScenarios() {
        return repo.findAllWithTagsOrderByCreatedAtDesc();
    }

    public long countAllScenarios() {
        return repo.count();
    }

    public long countPublishedScenarios() {
        return repo.countByVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);
    }

    public long countDraftScenarios() {
        return repo.countByVisibilityStatus(ScenarioVisibilityStatus.DRAFT);
    }

    public Scenario publishScenario(Long id, Authentication authentication) {
        Scenario scenario = getRequiredScenario(id);
        access.assertCanPublishScenario(scenario, authentication);
        boolean firstPublication = scenario.getVisibilityStatus() != ScenarioVisibilityStatus.PUBLISHED;

        if (scenario.getReviewStatus() == ReviewStatus.PENDING) {
            throw new AccessDeniedException("Ce fork doit être approuvé par l'auteur original avant publication.");
        }
        if (scenario.getReviewStatus() == ReviewStatus.REJECTED) {
            throw new AccessDeniedException("Ce fork a été rejeté par l'auteur original et ne peut pas être publié.");
        }

        scenario.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);
        scenario.setPublishedAt(Instant.now());

        Scenario saved = repo.save(scenario);

        Long actorId = userService.getUserByUsername(authentication.getName()).getId();
        scenarioHistoryService.record(
                saved.getId(),
                actorId,
                ScenarioHistoryAction.PUBLISHED,
                firstPublication ? "Published the scenario" : "Updated the published scenario"
        );

        if (firstPublication) {
            notificationService.notifyNewScenarioInLanguage(saved);
        }
        return saved;
    }

    public Scenario updateStoryboard(Long id, UpdateScenarioStoryboardRequest request, Authentication authentication) {
        Scenario scenario = getRequiredScenario(id);
        access.assertCanEditScenario(scenario, authentication);

        if (request.layoutMode() != null) {
            scenario.setStoryboardLayoutMode(StoryboardLayoutMode.valueOf(request.layoutMode().trim().toUpperCase()));
        }

        if (request.preset() != null && !request.preset().isBlank()) {
            scenario.setStoryboardPreset(request.preset().trim().toUpperCase());
        }

        if (request.columns() != null) {
            int columns = request.columns();
            if (columns < 1 || columns > 12) {
                throw new IllegalArgumentException("Storyboard columns must be between 1 and 12");
            }
            scenario.setStoryboardColumns(columns);
        }

        Scenario saved = repo.save(scenario);

        Long actorId = userService.getUserByUsername(authentication.getName()).getId();
        scenarioHistoryService.record(saved.getId(), actorId, ScenarioHistoryAction.STORYBOARD_UPDATED, "Updated the storyboard settings");

        return saved;
    }

    public Scenario updateScenarioMetadata(Long id, UpdateScenarioMetadataRequest request, Authentication authentication) {
        Scenario scenario = getRequiredScenario(id);
        access.assertCanEditScenario(scenario, authentication);

        List<String> changedFields = new ArrayList<>();

        if (request.title() != null) {
            String title = validatedTitle(request.title());
            if (!title.equalsIgnoreCase(scenario.getTitle())
                    && repo.existsByTitleIgnoreCaseAndAuthorUsernameAndLanguageId(
                    title,
                    userService.getUserById(scenario.getAuthor_id()).getUsername(),
                    scenario.getLanguage_id()
            )) {
                throw new IllegalArgumentException("Scenario already exists for this user and language");
            }
            scenario.setTitle(title);
            changedFields.add("title");
        }

        if (request.description() != null) {
            scenario.setDescription(validatedDescription(request.description()));
            changedFields.add("description");
        }

        if (request.tags() != null) {
            scenario.setTags(new LinkedHashSet<>(scenarioTagService.resolveTags(request.tags())));
            changedFields.add("tags");
        }

        if (request.location() != null) {
            ScenarioLocationDto location = request.location();
            Double latitude = location.latitude();
            Double longitude = location.longitude();
            String name = location.name() == null ? "" : location.name().trim();
            if (latitude == null && longitude == null && name.isEmpty()) {
                scenario.setLocationName(null);
                scenario.setLatitude(null);
                scenario.setLongitude(null);
            } else {
                if (name.length() > 200 || latitude == null || longitude == null
                        || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                        || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                    throw new IllegalArgumentException("A location requires valid latitude (-90 to 90) and longitude (-180 to 180), and a name of at most 200 characters");
                }
                scenario.setLocationName(name.isEmpty() ? null : name);
                scenario.setLatitude(latitude);
                scenario.setLongitude(longitude);
            }
            changedFields.add("location");
        }

        Scenario saved = repo.save(scenario);

        if (!changedFields.isEmpty()) {
            Long actorId = userService.getUserByUsername(authentication.getName()).getId();
            scenarioHistoryService.record(saved.getId(), actorId, ScenarioHistoryAction.METADATA_UPDATED,
                    "Updated " + String.join(", ", changedFields));
        }

        return saved;
    }

    /** Trims and checks a scenario title (also used for fork titles). */
    static String validatedTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (normalized.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("Title must be 200 characters or fewer");
        }
        return normalized;
    }

    private String validatedDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("Description must be 500 characters or fewer");
        }
        return normalized;
    }

    @Transactional
    public void deleteScenario(Long id, Authentication authentication) {
        Scenario scenario = getRequiredScenario(id);
        access.assertCanDeleteScenario(scenario, authentication);

        likeRepo.deleteByScenario_Id(id);
        bookmarkRepo.deleteByScenario_Id(id);
        collaboratorRepo.deleteByScenarioId(id);
        inviteLinkRepo.deleteByScenarioId(id);
        historyRepo.deleteByScenarioId(id);

        for (Thumbnail thumbnail : thumbnailRepo.findByScenarioIdOrderByIdxAsc(id)) {
            thumbnailService.delete(thumbnail.getId());
        }

        for (Audio audio : audioRepo.findByScenarioIdAndScopeOrderByIdxAscIdAsc(id, AudioScope.BACKGROUND)) {
            if (!audioRepo.existsByStoragePathAndIdNot(audio.getStoragePath(), audio.getId())) {
                storage.deleteAfterCommit(audio.getStoragePath());
            }
            audioRepo.delete(audio);
        }

        repo.delete(scenario);
    }

    public ScenarioDto toDto(Scenario s) {
        return toDto(s, null);
    }

    public ScenarioDto toDto(Scenario s, String viewerUsername) {
        String authorUsername = userService.getUserById(s.getAuthor_id()).getUsername();
        String reviewedByUsername = s.getReviewedById() != null
                ? userService.getUserById(s.getReviewedById()).getUsername()
                : null;
        boolean canEdit = viewerUsername != null && access.hasEditAccess(s.getId(), viewerUsername);

        return new ScenarioDto(
                s.getId(),
                s.getTitle(),
                s.getDescription(),
                s.getLanguage_id(),
                authorUsername,
                s.getCreatedAt(),
                s.getVisibilityStatus().name(),
                s.getPublishedAt(),
                s.getStoryboardLayoutMode().name(),
                s.getStoryboardPreset(),
                s.getStoryboardColumns(),
                scenarioTagService.toNames(s.getTags()),
                s.getParentScenarioId(),
                s.getReviewStatus().name(),
                reviewedByUsername,
                s.getReviewedAt(),
                s.getReviewComment(),
                canEdit,
                s.getLatitude() != null && s.getLongitude() != null
                        ? new ScenarioLocationDto(s.getLocationName(), s.getLatitude(), s.getLongitude()) : null
        );
    }

    public Scenario adminUpdateVisibility(Long id, String visibilityStatus) {
        Scenario scenario = getRequiredScenario(id);

        if (visibilityStatus == null || visibilityStatus.isBlank()) {
            throw new IllegalArgumentException("Visibility status is required");
        }

        ScenarioVisibilityStatus next = ScenarioVisibilityStatus.valueOf(visibilityStatus.trim().toUpperCase());
        scenario.setVisibilityStatus(next);

        if (next == ScenarioVisibilityStatus.PUBLISHED && scenario.getPublishedAt() == null) {
            scenario.setPublishedAt(Instant.now());
        }

        if (next != ScenarioVisibilityStatus.PUBLISHED) {
            scenario.setPublishedAt(null);
        }

        return repo.save(scenario);
    }

    public List<ScenarioDto> listVisibleScenarioDtos(Authentication authentication) {
        String username = authenticatedUsername(authentication);
        return listVisibleScenarios(authentication).stream()
                .map(s -> toDto(s, username))
                .toList();
    }

    public List<ScenarioDto> listMyScenarioDtos(Authentication authentication) {
        String username = authenticatedUsername(authentication);
        return listMyScenarios(authentication).stream()
                .map(s -> toDto(s, username))
                .toList();
    }

    public List<ScenarioDto> listScenariosByLanguageId(String languageId) {
        return repo.findAllByLanguageIdWithTagsOrderByCreatedAtDesc(languageId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<ScenarioDto> listPublishedScenariosByFamilyId(String familyId, Integer limit) {
        languageService.assertFamilyExists(familyId);
        Pageable pageable = PageRequest.of(0, clampDiscoveryLimit(limit));
        return repo.findPublishedByFamilyIdOrderByCreatedAtDesc(familyId, pageable)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<ScenarioDto> listPublishedScenariosByCountryIso(String isoA3, Integer limit) {
        List<String> languageIds = languageService.findLanguageIdsByCountryIsoA3(isoA3);
        if (languageIds.isEmpty()) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(0, clampDiscoveryLimit(limit));
        return repo.findPublishedByLanguageIdInOrderByCreatedAtDesc(languageIds, pageable)
                .stream()
                .map(this::toDto)
                .toList();
    }

    int clampDiscoveryLimit(Integer limit) {
        if (limit == null) {
            return DISCOVERY_SCENARIO_LIMIT;
        }
        return Math.max(1, Math.min(limit, DISCOVERY_SCENARIO_LIMIT));
    }
}
