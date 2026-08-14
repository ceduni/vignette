package org.titiplex.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.api.dto.UpdateScenarioMetadataRequest;
import org.titiplex.api.dto.UpdateScenarioStoryboardRequest;
import org.titiplex.persistence.model.CollaboratorRole;
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

@Service
public class ScenarioService {

    public static final int DISCOVERY_SCENARIO_LIMIT = 15;
    private static final int TITLE_MAX_LENGTH = 200;
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
            FileStorageService storage
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
    }

    public boolean existsByIdAndAuthorUsername(Long scenarioId, String username) {
        return repo.existsByIdAndAuthorUsername(scenarioId, username);
    }

    public boolean existsByTitleAndAuthorNameAndLanguageId(String title, String authorName, String languageId) {
        return repo.existsByTitleAndAuthorUsernameAndLanguageId(title, authorName, languageId);
    }

    public Scenario createScenario(String title, String description, Long authorId, String languageId, List<String> tags) {
        String normalizedTitle = validatedTitle(title);
        String normalizedDescription = validatedDescription(description);
        Scenario scenario = new Scenario();
        scenario.setTitle(normalizedTitle);
        scenario.setDescription(normalizedDescription);
        scenario.setAuthor_id(authorId);
        scenario.setLanguage_id(languageId);
        scenario.setCreatedAt(Instant.now());
        scenario.setAuthor(userService.getUserById(authorId));
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
        assertCanViewScenario(scenario, authentication);
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
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }

        return repo.findAllByAuthorUsernameWithTagsOrderByCreatedAtDesc(authentication.getName());
    }

    public List<Scenario> listSharedWithMeScenarios(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }

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
        return repo.findAllByOrderByCreatedAtDesc();
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
        assertCanPublishScenario(scenario, authentication);

        if (scenario.getReviewStatus() == ReviewStatus.PENDING) {
            throw new AccessDeniedException("Ce fork doit être approuvé par l'auteur original avant publication.");
        }
        if (scenario.getReviewStatus() == ReviewStatus.REJECTED) {
            throw new AccessDeniedException("Ce fork a été rejeté par l'auteur original et ne peut pas être publié.");
        }

        scenario.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);
        if (scenario.getPublishedAt() == null) {
            scenario.setPublishedAt(Instant.now());
        }

        Scenario saved = repo.save(scenario);

        Long actorId = userService.getUserByUsername(authentication.getName()).getId();
        scenarioHistoryService.record(saved.getId(), actorId, ScenarioHistoryAction.PUBLISHED, "Published the scenario");

        // Notify followers of this language
        notificationService.notifyNewScenarioInLanguage(saved);
        return saved;
    }

    public boolean hasEditAccess(Long scenarioId, String username) {
        if (username == null) return false;
        if (existsByIdAndAuthorUsername(scenarioId, username)) return true;
        return hasEditorAccess(scenarioId, username);
    }

    /**
     * Like {@link #hasEditAccess}, but also requires the scenario to still be a draft.
     * Once a scenario is published, its content is frozen for everyone but admins —
     * use this (not {@link #hasEditAccess}) to gate actual content-editing actions
     * (metadata, storyboard, thumbnails, audio). {@link #hasEditAccess} on its own
     * remains publish-agnostic since it also backs scenario deletion, which stays
     * allowed for the owner after publish.
     */
    public boolean hasContentEditAccess(Long scenarioId, String username) {
        return hasEditAccess(scenarioId, username);
    }

    public Scenario updateStoryboard(Long id, UpdateScenarioStoryboardRequest request, Authentication authentication) {
        Scenario scenario = getRequiredScenario(id);
        assertCanEditScenario(scenario, authentication);

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
        assertCanEditScenario(scenario, authentication);

        List<String> changedFields = new ArrayList<>();

        if (request.title() != null) {
            String title = validatedTitle(request.title());
            if (!title.equals(scenario.getTitle())
                    && repo.existsByTitleAndAuthorUsernameAndLanguageId(
                    title,
                    authentication.getName(),
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

        Scenario saved = repo.save(scenario);

        if (!changedFields.isEmpty()) {
            Long actorId = userService.getUserByUsername(authentication.getName()).getId();
            scenarioHistoryService.record(saved.getId(), actorId, ScenarioHistoryAction.METADATA_UPDATED,
                    "Updated " + String.join(", ", changedFields));
        }

        return saved;
    }

    private String validatedTitle(String title) {
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
        assertCanDeleteScenario(scenario, authentication);

        likeRepo.deleteByScenario_Id(id);
        bookmarkRepo.deleteByScenario_Id(id);
        collaboratorRepo.deleteByScenarioId(id);
        inviteLinkRepo.deleteByScenarioId(id);
        historyRepo.deleteByScenarioId(id);

        for (Thumbnail thumbnail : thumbnailRepo.getThumbnailsByScenarioId(id)) {
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

    public void assertCanDeleteScenario(Scenario scenario, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
        if (isAdmin(authentication)) {
            return;
        }

        String username = authenticatedUsername(authentication);
        if (username != null && existsByIdAndAuthorUsername(scenario.getId(), username)) {
            return;
        }

        throw new AccessDeniedException("Only the scenario's author can delete it");
    }

    public void assertCanViewScenario(Scenario scenario, Authentication authentication) {
        boolean publiclyVisible = scenario.getVisibilityStatus() == ScenarioVisibilityStatus.PUBLISHED
                && scenario.getReviewStatus() != ReviewStatus.PENDING
                && scenario.getReviewStatus() != ReviewStatus.REJECTED;

        if (publiclyVisible) {
            return;
        }
        if (isAdmin(authentication)) {
            return;
        }

        String username = authenticatedUsername(authentication);
        if (username != null && existsByIdAndAuthorUsername(scenario.getId(), username)) {
            return;
        }

        if (username != null && isAcceptedCollaborator(scenario.getId(), username)) {
            return;
        }

        // A user with a pending collaboration invitation can preview the scenario
        // before deciding whether to accept
        if (username != null && hasPendingInvitation(scenario.getId(), username)) {
            return;
        }

        // Original author can view a pending/rejected fork of their own scenario
        if (username != null && scenario.getParentScenarioId() != null) {
            Scenario original = repo.findById(scenario.getParentScenarioId()).orElse(null);
            if (original != null && username.equals(userService.getUserById(original.getAuthor_id()).getUsername())) {
                return;
            }
        }

        throw new NoSuchElementException("Scenario not found");
    }

    public void assertCanEditScenario(Scenario scenario, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
        if (isAdmin(authentication)) {
            return;
        }

        String username = authenticatedUsername(authentication);
        if (username != null && existsByIdAndAuthorUsername(scenario.getId(), username)) {
            return;
        }

        if (username != null && hasEditorAccess(scenario.getId(), username)) {
            return;
        }

        throw new AccessDeniedException("You are not allowed to edit this scenario");
    }

    public void assertCanPublishScenario(Scenario scenario, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
        if (isAdmin(authentication)) {
            return;
        }

        String username = authenticatedUsername(authentication);
        if (username != null && existsByIdAndAuthorUsername(scenario.getId(), username)) {
            return;
        }

        throw new AccessDeniedException("Seul l'auteur original peut publier ce scénario.");
    }

    private boolean isAcceptedCollaborator(Long scenarioId, String username) {
        Long userId = userService.getUserByUsername(username).getId();
        return collaboratorRepo.findByScenarioIdAndUserId(scenarioId, userId)
                .filter(c -> c.getStatus() == CollaborationStatus.ACCEPTED)
                .isPresent();
    }

    private boolean hasPendingInvitation(Long scenarioId, String username) {
        Long userId = userService.getUserByUsername(username).getId();
        return collaboratorRepo.findByScenarioIdAndUserId(scenarioId, userId)
                .filter(c -> c.getStatus() == CollaborationStatus.PENDING)
                .isPresent();
    }

    private boolean hasEditorAccess(Long scenarioId, String username) {
        Long userId = userService.getUserByUsername(username).getId();
        return collaboratorRepo.findByScenarioIdAndUserId(scenarioId, userId)
                .filter(c -> c.getStatus() == CollaborationStatus.ACCEPTED)
                .filter(c -> c.getRole() == CollaboratorRole.OWNER || c.getRole() == CollaboratorRole.EDITOR)
                .isPresent();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    public ScenarioDto toDto(Scenario s) {
        return toDto(s, null);
    }

    public ScenarioDto toDto(Scenario s, String viewerUsername) {
        String authorUsername = userService.getUserById(s.getAuthor_id()).getUsername();
        String reviewedByUsername = s.getReviewedById() != null
                ? userService.getUserById(s.getReviewedById()).getUsername()
                : null;
        boolean canEdit = viewerUsername != null && hasEditAccess(s.getId(), viewerUsername);

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
                canEdit
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

    public Scenario forkScenario(Long originalId, String requestedTitle, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }

        Scenario original = getRequiredScenario(originalId);

        if (original.getVisibilityStatus() != ScenarioVisibilityStatus.PUBLISHED) {
            throw new IllegalArgumentException("Cannot fork an unpublished scenario");
        }

        String username = authentication.getName();
        Long userId = userService.getUserByUsername(username).getId();

        String title;
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            title = validatedTitle(requestedTitle);
            if (repo.existsByTitleAndAuthorUsernameAndLanguageId(title, username, original.getLanguage_id())) {
                throw new IllegalArgumentException("Scenario already exists for this user and language");
            }
        } else {
            title = generateUniqueScenarioTitle(original.getTitle(), username, original.getLanguage_id());
        }

        Scenario fork = new Scenario();
        fork.setTitle(title);
        fork.setDescription(original.getDescription());
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

        Scenario saved = repo.save(fork);

        // Deep copy: thumbnails + audios. Physical files are reused (content-addressed
        // storage), only new DB rows are created pointing at the same storagePath/hash.
        List<Thumbnail> originalThumbnails = thumbnailRepo.findByScenarioIdOrderByIdxAsc(original.getId());

        for (Thumbnail ot : originalThumbnails) {
            Thumbnail copy = new Thumbnail();
            copy.setTitle(ot.getTitle());
            copy.setIdx(ot.getIdx());
            copy.setContentType(ot.getContentType());
            copy.setAuthorId(userId);
            copy.setScenarioId(saved.getId());
            copy.setStoragePath(ot.getStoragePath());
            copy.setSizeBytes(ot.getSizeBytes());
            copy.setOriginalFilename(ot.getOriginalFilename());
            copy.setImageSha256(ot.getImageSha256());
            copy.setGridColumn(ot.getGridColumn());
            copy.setGridRow(ot.getGridRow());
            copy.setGridColumnSpan(ot.getGridColumnSpan());
            copy.setGridRowSpan(ot.getGridRowSpan());
            copy.setImageWidth(ot.getImageWidth());
            copy.setImageHeight(ot.getImageHeight());
            Thumbnail savedThumb = thumbnailRepo.save(copy);

            List<Audio> originalAudios = audioRepo.findByThumbnailIdOrderByIdxAsc(ot.getId());
            for (Audio oa : originalAudios) {
                Audio audioCopy = new Audio();
                audioCopy.setStoragePath(oa.getStoragePath());
                audioCopy.setSizeBytes(oa.getSizeBytes());
                audioCopy.setOriginalFilename(oa.getOriginalFilename());
                audioCopy.setAudioSha256(oa.getAudioSha256());
                audioCopy.setTitle(oa.getTitle());
                audioCopy.setIdx(oa.getIdx());
                audioCopy.setMime(oa.getMime());
                audioCopy.setAuthorId(userId);
                audioCopy.setScenarioId(saved.getId());
                audioCopy.setLanguageId(oa.getLanguageId());
                audioCopy.setThumbnailId(savedThumb.getId());
                audioCopy.setMarkerX(oa.getMarkerX());
                audioCopy.setMarkerY(oa.getMarkerY());
                audioCopy.setMarkerLabel(oa.getMarkerLabel());
                audioRepo.save(audioCopy);
            }
        }

        for (Audio originalAudio : audioRepo.findByScenarioIdAndScopeOrderByIdxAscIdAsc(
                original.getId(),
                AudioScope.BACKGROUND
        )) {
            Audio audioCopy = new Audio();
            audioCopy.setStoragePath(originalAudio.getStoragePath());
            audioCopy.setSizeBytes(originalAudio.getSizeBytes());
            audioCopy.setOriginalFilename(originalAudio.getOriginalFilename());
            audioCopy.setAudioSha256(originalAudio.getAudioSha256());
            audioCopy.setTitle(originalAudio.getTitle());
            audioCopy.setIdx(originalAudio.getIdx());
            audioCopy.setMime(originalAudio.getMime());
            audioCopy.setScope(AudioScope.BACKGROUND);
            audioCopy.setAuthorId(userId);
            audioCopy.setScenarioId(saved.getId());
            audioCopy.setLanguageId(originalAudio.getLanguageId());
            audioCopy.setSourceLabel(originalAudio.getSourceLabel());
            audioCopy.setSourceUrl(originalAudio.getSourceUrl());
            audioRepo.save(audioCopy);
        }

        if (saved.getReviewStatus() == ReviewStatus.PENDING) {
            notificationService.notifyForkReviewRequested(original.getAuthor_id(), saved, original, userId);
        }

        return saved;
    }

    @Transactional
    public Scenario reviewFork(Long forkId, boolean approve, String comment, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }

        Scenario fork = getRequiredScenario(forkId);

        if (fork.getReviewStatus() != ReviewStatus.PENDING) {
            throw new IllegalStateException("Ce scénario n'est pas en attente de review.");
        }
        if (fork.getParentScenarioId() == null) {
            throw new IllegalStateException("Ce scénario n'est pas un fork.");
        }

        Scenario original = getRequiredScenario(fork.getParentScenarioId());
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

    private String generateUniqueScenarioTitle(String baseTitle, String username, String languageId) {
        String normalizedBase = baseTitle == null ? "Untitled scenario" : baseTitle.trim();
        String candidate = copyTitle(normalizedBase, null);
        int counter = 2;
        while (repo.existsByTitleAndAuthorUsernameAndLanguageId(candidate, username, languageId)) {
            candidate = copyTitle(normalizedBase, counter);
            counter++;
        }
        return candidate;
    }

    private String copyTitle(String baseTitle, Integer counter) {
        String suffix = counter == null ? "" : " #" + counter;
        String prefix = "Copy of ";
        int available = TITLE_MAX_LENGTH - prefix.length() - suffix.length();
        String truncatedBase = baseTitle.length() <= available ? baseTitle : baseTitle.substring(0, available).trim();
        return prefix + truncatedBase + suffix;
    }
}
