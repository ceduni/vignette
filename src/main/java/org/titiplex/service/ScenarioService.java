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
import org.titiplex.persistence.model.ReviewStatus;
import org.titiplex.persistence.model.Thumbnail;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.AudioRepository;
import org.titiplex.persistence.repo.ThumbnailRepository;

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

    private final ScenarioRepository repo;
    private final UserService userService;
    private final LanguageService languageService;
    private final ScenarioTagService scenarioTagService;
    private final NotificationService notificationService;
    private final ThumbnailRepository thumbnailRepo;
    private final AudioRepository audioRepo;
    private final ScenarioCollaboratorRepository collaboratorRepo;
    private final ScenarioHistoryService scenarioHistoryService;

    public ScenarioService(
            ScenarioRepository scenarioRepository,
            UserService userService,
            LanguageService languageService,
            ScenarioTagService scenarioTagService,
            NotificationService notificationService,
            ThumbnailRepository thumbnailRepo,
            AudioRepository audioRepo,
            ScenarioCollaboratorRepository collaboratorRepo,
            ScenarioHistoryService scenarioHistoryService
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
    }

    public boolean existsByIdAndAuthorUsername(Long scenarioId, String username) {
        return repo.existsByIdAndAuthorUsername(scenarioId, username);
    }

    public boolean existsByTitleAndAuthorNameAndLanguageId(String title, String authorName, String languageId) {
        return repo.existsByTitleAndAuthorUsernameAndLanguageId(title, authorName, languageId);
    }

    public Scenario createScenario(String title, String description, Long authorId, String languageId, List<String> tags) {
        Scenario scenario = new Scenario();
        scenario.setTitle(title);
        scenario.setDescription(description);
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
        if (!hasEditAccess(scenarioId, username)) return false;
        Scenario scenario = repo.findById(scenarioId).orElse(null);
        return scenario != null && scenario.getVisibilityStatus() != ScenarioVisibilityStatus.PUBLISHED;
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
            if (columns < 1 || columns > 8) {
                throw new IllegalArgumentException("Storyboard columns must be between 1 and 8");
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
            String title = request.title().trim();
            if (title.isBlank()) {
                throw new IllegalArgumentException("Title is required");
            }
            scenario.setTitle(title);
            changedFields.add("title");
        }

        if (request.description() != null) {
            scenario.setDescription(request.description().trim());
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

    public void deleteScenario(Long id) {
        repo.findById(id).ifPresent(repo::delete);
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
        if (scenario.getVisibilityStatus() == ScenarioVisibilityStatus.PUBLISHED) {
            throw new AccessDeniedException("Published scenarios can no longer be edited.");
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
        boolean canEdit = viewerUsername != null
                && s.getVisibilityStatus() != ScenarioVisibilityStatus.PUBLISHED
                && hasEditAccess(s.getId(), viewerUsername);

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
            title = requestedTitle.trim();
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
        fork.setReviewComment(comment);

        Scenario saved = repo.save(fork);
        notificationService.notifyForkReviewed(saved.getAuthor_id(), saved, approve);
        return saved;
    }

    private String generateUniqueScenarioTitle(String baseTitle, String username, String languageId) {
        String candidate = "Copy of " + baseTitle;
        int counter = 2;
        while (repo.existsByTitleAndAuthorUsernameAndLanguageId(candidate, username, languageId)) {
            candidate = "Copy of " + baseTitle + " #" + counter;
            counter++;
        }
        return candidate;
    }
}