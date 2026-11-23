package org.titiplex.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.api.dto.ScenarioLocationDto;
import org.titiplex.api.dto.UpdateScenarioMetadataRequest;
import org.titiplex.api.dto.UpdateScenarioStoryboardRequest;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.ReviewStatus;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioCollaborator;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.ScenarioVisibilityStatus;
import org.titiplex.persistence.model.StoryboardLayoutMode;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.titiplex.util.AuthUtils.authenticatedUsername;
import static org.titiplex.util.AuthUtils.isAdmin;
import static org.titiplex.util.AuthUtils.requireAuthenticated;

/**
 * Scenarios themselves: creating them, finding and listing them, editing their description
 * and storyboard settings, and publishing them.
 * <p>
 * Related concerns live in their own services: permissions ({@link ScenarioAccessService}),
 * copies and their review ({@link ScenarioForkService}), deletion
 * ({@link ScenarioDeletionService}) and collaborators ({@link ScenarioCollaborationService}).
 */
@Service
public class ScenarioService {

    public static final int DISCOVERY_SCENARIO_LIMIT = 15;
    static final int TITLE_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int LOCATION_NAME_MAX_LENGTH = 200;

    private final ScenarioRepository repo;
    private final UserService userService;
    private final LanguageService languageService;
    private final ScenarioTagService scenarioTagService;
    private final NotificationService notificationService;
    private final ScenarioCollaboratorRepository collaboratorRepo;
    private final ScenarioHistoryService scenarioHistoryService;
    private final ScenarioAccessService access;

    public ScenarioService(
            ScenarioRepository scenarioRepository,
            UserService userService,
            LanguageService languageService,
            ScenarioTagService scenarioTagService,
            NotificationService notificationService,
            ScenarioCollaboratorRepository collaboratorRepo,
            ScenarioHistoryService scenarioHistoryService,
            ScenarioAccessService access
    ) {
        this.repo = scenarioRepository;
        this.userService = userService;
        this.languageService = languageService;
        this.scenarioTagService = scenarioTagService;
        this.notificationService = notificationService;
        this.collaboratorRepo = collaboratorRepo;
        this.scenarioHistoryService = scenarioHistoryService;
        this.access = access;
    }

    // ── Creating ───────────────────────────────────────────────────────

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

    // ── Finding one scenario ───────────────────────────────────────────

    public Scenario getRequiredScenario(Long id) {
        return repo.findByIdWithTags(id)
                .orElseThrow(() -> new NoSuchElementException("Scenario not found"));
    }

    public Scenario getVisibleScenario(Long id, Authentication authentication) {
        Scenario scenario = getRequiredScenario(id);
        access.assertCanViewScenario(scenario, authentication);
        return scenario;
    }

    // ── Listing, for the current user ──────────────────────────────────

    /** Everything for admins; published scenarios for visitors; published plus their own for users. */
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

    public List<ScenarioDto> listVisibleScenarioDtos(Authentication authentication) {
        return toDtos(listVisibleScenarios(authentication), authenticatedUsername(authentication));
    }

    public List<Scenario> listMyScenarios(Authentication authentication) {
        requireAuthenticated(authentication);

        return repo.findAllByAuthorUsernameWithTagsOrderByCreatedAtDesc(authentication.getName());
    }

    public List<ScenarioDto> listMyScenarioDtos(Authentication authentication) {
        return toDtos(listMyScenarios(authentication), authenticatedUsername(authentication));
    }

    /** Scenarios of other authors on which the user is an accepted collaborator. */
    public List<Scenario> listSharedWithMeScenarios(Authentication authentication) {
        requireAuthenticated(authentication);

        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        List<Long> scenarioIds = acceptedCollaborationScenarioIds(userId);
        if (scenarioIds.isEmpty()) {
            return List.of();
        }

        return repo.findAllByIdInWithTags(scenarioIds);
    }

    public List<ScenarioDto> listSharedWithMeScenarioDtos(Authentication authentication) {
        return toDtos(listSharedWithMeScenarios(authentication), authenticatedUsername(authentication));
    }

    // ── Listing, public discovery ──────────────────────────────────────

    /**
     * Published scenarios a user has worked on — either as author or as an accepted
     * collaborator. Drafts are always excluded, regardless of the viewer.
     */
    public List<ScenarioDto> listPublishedScenariosWorkedOnByUsername(String username) {
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        List<Long> collaboratedIds = acceptedCollaborationScenarioIds(user.getId());

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
                .map(this::toDto)
                .toList();
    }

    public List<ScenarioDto> listScenariosByLanguageId(String languageId) {
        return toDtos(repo.findAllByLanguageIdWithTagsOrderByCreatedAtDesc(languageId), null);
    }

    public List<ScenarioDto> listPublishedScenariosByFamilyId(String familyId, Integer limit) {
        languageService.assertFamilyExists(familyId);
        Pageable pageable = PageRequest.of(0, clampDiscoveryLimit(limit));
        return toDtos(repo.findPublishedByFamilyIdOrderByCreatedAtDesc(familyId, pageable), null);
    }

    public List<ScenarioDto> listPublishedScenariosByCountryIso(String isoA3, Integer limit) {
        List<String> languageIds = languageService.findLanguageIdsByCountryIsoA3(isoA3);
        if (languageIds.isEmpty()) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(0, clampDiscoveryLimit(limit));
        return toDtos(repo.findPublishedByLanguageIdInOrderByCreatedAtDesc(languageIds, pageable), null);
    }

    int clampDiscoveryLimit(Integer limit) {
        if (limit == null) {
            return DISCOVERY_SCENARIO_LIMIT;
        }
        return Math.max(1, Math.min(limit, DISCOVERY_SCENARIO_LIMIT));
    }

    // ── Administration ─────────────────────────────────────────────────

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

    /** Lets an admin publish or unpublish any scenario, bypassing the author and the fork review. */
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

    // ── Publishing ─────────────────────────────────────────────────────

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

        recordHistory(saved, authentication, ScenarioHistoryAction.PUBLISHED,
                firstPublication ? "Published the scenario" : "Updated the published scenario");

        if (firstPublication) {
            notificationService.notifyNewScenarioInLanguage(saved);
        }
        return saved;
    }

    // ── Editing ────────────────────────────────────────────────────────

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
        recordHistory(saved, authentication, ScenarioHistoryAction.STORYBOARD_UPDATED, "Updated the storyboard settings");
        return saved;
    }

    /** Updates the fields present in the request (title, description, tags, location); absent ones are left as they are. */
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
            applyLocation(scenario, request.location());
            changedFields.add("location");
        }

        Scenario saved = repo.save(scenario);

        if (!changedFields.isEmpty()) {
            recordHistory(saved, authentication, ScenarioHistoryAction.METADATA_UPDATED,
                    "Updated " + String.join(", ", changedFields));
        }

        return saved;
    }

    /** A location with no coordinates and no name clears the scenario's location. */
    private static void applyLocation(Scenario scenario, ScenarioLocationDto location) {
        Double latitude = location.latitude();
        Double longitude = location.longitude();
        String name = location.name() == null ? "" : location.name().trim();

        if (latitude == null && longitude == null && name.isEmpty()) {
            scenario.setLocationName(null);
            scenario.setLatitude(null);
            scenario.setLongitude(null);
            return;
        }

        if (name.length() > LOCATION_NAME_MAX_LENGTH || latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("A location requires valid latitude (-90 to 90) and longitude (-180 to 180), and a name of at most 200 characters");
        }
        scenario.setLocationName(name.isEmpty() ? null : name);
        scenario.setLatitude(latitude);
        scenario.setLongitude(longitude);
    }

    // ── Validation ─────────────────────────────────────────────────────

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

    // ── DTO ────────────────────────────────────────────────────────────

    public ScenarioDto toDto(Scenario s) {
        return toDto(s, null);
    }

    /** @param viewerUsername the user the DTO is built for, to tell whether they may edit; null for an anonymous view */
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

    private List<ScenarioDto> toDtos(List<Scenario> scenarios, String viewerUsername) {
        return scenarios.stream()
                .map(s -> toDto(s, viewerUsername))
                .toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private List<Long> acceptedCollaborationScenarioIds(Long userId) {
        return collaboratorRepo.findByUserIdAndStatus(userId, CollaborationStatus.ACCEPTED)
                .stream()
                .map(ScenarioCollaborator::getScenarioId)
                .toList();
    }

    private void recordHistory(Scenario scenario, Authentication authentication, ScenarioHistoryAction action, String summary) {
        Long actorId = userService.getUserByUsername(authentication.getName()).getId();
        scenarioHistoryService.record(scenario.getId(), actorId, action, summary);
    }
}
