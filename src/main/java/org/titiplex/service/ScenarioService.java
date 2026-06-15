package org.titiplex.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.titiplex.api.dto.ScenarioDto;
import org.titiplex.api.dto.UpdateScenarioMetadataRequest;
import org.titiplex.api.dto.UpdateScenarioStoryboardRequest;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioVisibilityStatus;
import org.titiplex.persistence.model.StoryboardLayoutMode;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ScenarioService {
    private final ScenarioRepository repo;
    private final UserService userService;
    private final LanguageService languageService;
    private final ScenarioTagService scenarioTagService;

    public ScenarioService(
            ScenarioRepository scenarioRepository,
            UserService userService,
            LanguageService languageService,
            ScenarioTagService scenarioTagService
    ) {
        this.repo = scenarioRepository;
        this.userService = userService;
        this.languageService = languageService;
        this.scenarioTagService = scenarioTagService;
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
        return repo.save(scenario);
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
        assertCanEditScenario(scenario, authentication);

        scenario.setVisibilityStatus(ScenarioVisibilityStatus.PUBLISHED);
        if (scenario.getPublishedAt() == null) {
            scenario.setPublishedAt(Instant.now());
        }

        return repo.save(scenario);
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

        return repo.save(scenario);
    }

    public Scenario updateScenarioMetadata(Long id, UpdateScenarioMetadataRequest request, Authentication authentication) {
        Scenario scenario = getRequiredScenario(id);
        assertCanEditScenario(scenario, authentication);

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isBlank()) {
                throw new IllegalArgumentException("Title is required");
            }
            scenario.setTitle(title);
        }

        if (request.description() != null) {
            scenario.setDescription(request.description().trim());
        }

        if (request.tags() != null) {
            scenario.setTags(new LinkedHashSet<>(scenarioTagService.resolveTags(request.tags())));
        }

        return repo.save(scenario);
    }

    public void deleteScenario(Long id) {
        repo.findById(id).ifPresent(repo::delete);
    }

    public void assertCanViewScenario(Scenario scenario, Authentication authentication) {
        if (scenario.getVisibilityStatus() == ScenarioVisibilityStatus.PUBLISHED) {
            return;
        }
        if (isAdmin(authentication)) {
            return;
        }

        String username = authenticatedUsername(authentication);
        if (username != null && existsByIdAndAuthorUsername(scenario.getId(), username)) {
            return;
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

        throw new AccessDeniedException("You are not allowed to edit this scenario");
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
        String authorUsername = userService.getUserById(s.getAuthor_id()).getUsername();

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
                s.getParentScenarioId()
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
        return listVisibleScenarios(authentication).stream()
                .map(this::toDto)
                .toList();
    }

    public List<ScenarioDto> listMyScenarioDtos(Authentication authentication) {
        return listMyScenarios(authentication).stream()
                .map(this::toDto)
                .toList();
    }
    public List<ScenarioDto> listScenariosByLanguageId(String languageId) {
        return repo.findAllByLanguageIdWithTagsOrderByCreatedAtDesc(languageId)
                .stream()
                .map(this::toDto)
                .toList();
    }
    public Scenario forkScenario(Long originalId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }

        Scenario original = getRequiredScenario(originalId);

        if (original.getVisibilityStatus() != ScenarioVisibilityStatus.PUBLISHED) {
            throw new IllegalArgumentException("Cannot fork an unpublished scenario");
        }

        String username = authentication.getName();
        Long userId = userService.getUserByUsername(username).getId();

        Scenario fork = new Scenario();
        fork.setTitle("Fork of " + original.getTitle());
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
        fork.setTags(new LinkedHashSet<>(original.getTags()));
        fork.setParentScenarioId(originalId);

        return repo.save(fork);
    }
}