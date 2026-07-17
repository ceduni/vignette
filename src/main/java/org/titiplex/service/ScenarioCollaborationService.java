package org.titiplex.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.api.dto.ScenarioCollaboratorDto;
import org.titiplex.api.dto.ScenarioInviteLinkDto;
import org.titiplex.persistence.model.*;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioInviteLinkRepository;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ScenarioCollaborationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final ScenarioRepository scenarioRepo;
    private final ScenarioCollaboratorRepository collaboratorRepo;
    private final ScenarioInviteLinkRepository inviteLinkRepo;
    private final UserService userService;
    private final NotificationService notificationService;

    public ScenarioCollaborationService(
            ScenarioRepository scenarioRepo,
            ScenarioCollaboratorRepository collaboratorRepo,
            ScenarioInviteLinkRepository inviteLinkRepo,
            UserService userService,
            NotificationService notificationService
    ) {
        this.scenarioRepo = scenarioRepo;
        this.collaboratorRepo = collaboratorRepo;
        this.inviteLinkRepo = inviteLinkRepo;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    // ── Role resolution (used by ScenarioService for permission checks) ────

    /**
     * Returns the effective role of a user on a scenario, or null if they
     * have no relationship to it. The original author is always OWNER,
     * even without a row in scenario_collaborator.
     */
    public CollaboratorRole getUserRole(Scenario scenario, Long userId) {
        if (userId == null) return null;
        if (userId.equals(scenario.getAuthor_id())) return CollaboratorRole.OWNER;

        return collaboratorRepo.findByScenarioIdAndUserId(scenario.getId(), userId)
                .filter(c -> c.getStatus() == CollaborationStatus.ACCEPTED)
                .map(ScenarioCollaborator::getRole)
                .orElse(null);
    }

    public boolean canEdit(Scenario scenario, Long userId) {
        CollaboratorRole role = getUserRole(scenario, userId);
        return role == CollaboratorRole.OWNER || role == CollaboratorRole.EDITOR;
    }

    public boolean canView(Scenario scenario, Long userId) {
        return getUserRole(scenario, userId) != null;
    }

    // ── Listing ──────────────────────────────────────────────────────────

    public List<ScenarioCollaborator> listCollaborators(Long scenarioId) {
        return collaboratorRepo.findByScenarioId(scenarioId);
    }

    public List<ScenarioCollaborator> listPendingInvitationsForUser(Authentication authentication) {
        requireAuthenticated(authentication);
        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        return collaboratorRepo.findByUserIdAndStatus(userId, CollaborationStatus.PENDING);
    }

    // ── Invitation by username ──────────────────────────────────────────

    @Transactional
    public ScenarioCollaborator inviteByUsername(Long scenarioId, String targetUsername, CollaboratorRole role, Authentication authentication) {
        Scenario scenario = requireScenario(scenarioId);
        Long inviterId = requireOwner(scenario, authentication);

        if (role == CollaboratorRole.OWNER) {
            throw new IllegalArgumentException("Cannot invite someone as OWNER; use ownership transfer instead");
        }

        User target = userService.getUserByUsername(targetUsername);
        if (target.getId().equals(scenario.getAuthor_id())) {
            throw new IllegalArgumentException("This user is already the owner of the scenario");
        }
        if (collaboratorRepo.existsByScenarioIdAndUserId(scenarioId, target.getId())) {
            throw new IllegalStateException("This user is already a collaborator or has a pending invitation");
        }

        ScenarioCollaborator collaborator = new ScenarioCollaborator();
        collaborator.setScenarioId(scenarioId);
        collaborator.setUserId(target.getId());
        collaborator.setRole(role);
        collaborator.setStatus(CollaborationStatus.PENDING);
        collaborator.setInvitedById(inviterId);
        collaborator.setInvitedAt(Instant.now());

        ScenarioCollaborator saved = collaboratorRepo.save(collaborator);
        notificationService.notifyCollaborationInvite(target.getId(), scenario, inviterId, role.name());
        return saved;
    }

    @Transactional
    public ScenarioCollaborator respondToInvitation(Long collaboratorId, boolean accept, Authentication authentication) {
        requireAuthenticated(authentication);
        Long userId = userService.getUserByUsername(authentication.getName()).getId();

        ScenarioCollaborator collaborator = collaboratorRepo.findById(collaboratorId)
                .orElseThrow(() -> new NoSuchElementException("Invitation not found"));

        if (!collaborator.getUserId().equals(userId)) {
            throw new AccessDeniedException("This invitation does not belong to you");
        }
        if (collaborator.getStatus() != CollaborationStatus.PENDING) {
            throw new IllegalStateException("This invitation has already been answered");
        }

        collaborator.setStatus(accept ? CollaborationStatus.ACCEPTED : CollaborationStatus.DECLINED);
        collaborator.setRespondedAt(Instant.now());
        ScenarioCollaborator saved = collaboratorRepo.save(collaborator);

        if (accept) {
            Scenario scenario = requireScenario(collaborator.getScenarioId());
            notificationService.notifyInviteAccepted(scenario.getAuthor_id(), scenario, userId);
        }

        return saved;
    }

    // ── Management (owner only) ─────────────────────────────────────────

    @Transactional
    public void removeCollaborator(Long scenarioId, Long targetUserId, Authentication authentication) {
        Scenario scenario = requireScenario(scenarioId);
        requireOwner(scenario, authentication);

        if (targetUserId.equals(scenario.getAuthor_id())) {
            throw new IllegalArgumentException("Cannot remove the scenario owner");
        }

        collaboratorRepo.deleteByScenarioIdAndUserId(scenarioId, targetUserId);
    }

    @Transactional
    public ScenarioCollaborator changeRole(Long scenarioId, Long targetUserId, CollaboratorRole newRole, Authentication authentication) {
        Scenario scenario = requireScenario(scenarioId);
        requireOwner(scenario, authentication);

        if (newRole == CollaboratorRole.OWNER) {
            throw new IllegalArgumentException("Cannot assign OWNER role; use ownership transfer instead");
        }
        if (targetUserId.equals(scenario.getAuthor_id())) {
            throw new IllegalArgumentException("Cannot change the role of the scenario owner");
        }

        ScenarioCollaborator collaborator = collaboratorRepo.findByScenarioIdAndUserId(scenarioId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Collaborator not found"));

        collaborator.setRole(newRole);
        return collaboratorRepo.save(collaborator);
    }

    // ── Invite links ─────────────────────────────────────────────────────

    @Transactional
    public ScenarioInviteLink createInviteLink(Long scenarioId, CollaboratorRole role, Instant expiresAt, Integer maxUses, Authentication authentication) {
        Scenario scenario = requireScenario(scenarioId);
        Long creatorId = requireOwner(scenario, authentication);

        if (role == CollaboratorRole.OWNER) {
            throw new IllegalArgumentException("Cannot create an invite link granting OWNER role");
        }

        ScenarioInviteLink link = new ScenarioInviteLink();
        link.setScenarioId(scenarioId);
        link.setToken(generateToken());
        link.setRole(role);
        link.setCreatedById(creatorId);
        link.setCreatedAt(Instant.now());
        link.setExpiresAt(expiresAt);
        link.setMaxUses(maxUses);
        link.setActive(true);

        return inviteLinkRepo.save(link);
    }

    public List<ScenarioInviteLink> listInviteLinks(Long scenarioId, Authentication authentication) {
        Scenario scenario = requireScenario(scenarioId);
        requireOwner(scenario, authentication);
        return inviteLinkRepo.findByScenarioId(scenarioId);
    }

    @Transactional
    public void revokeInviteLink(Long linkId, Authentication authentication) {
        ScenarioInviteLink link = inviteLinkRepo.findById(linkId)
                .orElseThrow(() -> new NoSuchElementException("Invite link not found"));
        Scenario scenario = requireScenario(link.getScenarioId());
        requireOwner(scenario, authentication);

        link.setActive(false);
        inviteLinkRepo.save(link);
    }

    @Transactional
    public ScenarioCollaborator joinViaLink(String token, Authentication authentication) {
        requireAuthenticated(authentication);
        Long userId = userService.getUserByUsername(authentication.getName()).getId();

        ScenarioInviteLink link = inviteLinkRepo.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("Invalid invite link"));

        if (!link.isActive()) {
            throw new IllegalStateException("This invite link has been revoked");
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("This invite link has expired");
        }
        if (link.getMaxUses() != null && link.getUseCount() >= link.getMaxUses()) {
            throw new IllegalStateException("This invite link has reached its maximum number of uses");
        }

        Scenario scenario = requireScenario(link.getScenarioId());

        if (userId.equals(scenario.getAuthor_id())) {
            throw new IllegalArgumentException("You are already the owner of this scenario");
        }

        var existing = collaboratorRepo.findByScenarioIdAndUserId(scenario.getId(), userId);
        if (existing.isPresent()) {
            ScenarioCollaborator collaborator = existing.get();
            if (collaborator.getStatus() == CollaborationStatus.ACCEPTED) {
                return collaborator; // already a collaborator, idempotent
            }
            collaborator.setStatus(CollaborationStatus.ACCEPTED);
            collaborator.setRole(link.getRole());
            collaborator.setRespondedAt(Instant.now());
            link.setUseCount(link.getUseCount() + 1);
            inviteLinkRepo.save(link);
            return collaboratorRepo.save(collaborator);
        }

        ScenarioCollaborator collaborator = new ScenarioCollaborator();
        collaborator.setScenarioId(scenario.getId());
        collaborator.setUserId(userId);
        collaborator.setRole(link.getRole());
        collaborator.setStatus(CollaborationStatus.ACCEPTED);
        collaborator.setInvitedById(link.getCreatedById());
        collaborator.setInvitedAt(Instant.now());
        collaborator.setRespondedAt(Instant.now());

        link.setUseCount(link.getUseCount() + 1);
        inviteLinkRepo.save(link);

        ScenarioCollaborator saved = collaboratorRepo.save(collaborator);
        notificationService.notifyInviteAccepted(scenario.getAuthor_id(), scenario, userId);
        return saved;
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }

    public ScenarioCollaboratorDto toDto(ScenarioCollaborator c) {
        User user = userService.getUserById(c.getUserId());
        String invitedByUsername = c.getInvitedById() != null
                ? userService.getUserById(c.getInvitedById()).getUsername()
                : null;
        String scenarioTitle = scenarioRepo.findById(c.getScenarioId())
                .map(Scenario::getTitle)
                .orElse("Unknown scenario");

        return new ScenarioCollaboratorDto(
                c.getId(),
                c.getScenarioId(),
                scenarioTitle,
                c.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                c.getRole().name(),
                c.getStatus().name(),
                invitedByUsername,
                c.getInvitedAt(),
                c.getRespondedAt()
        );
    }

    public ScenarioInviteLinkDto toDto(ScenarioInviteLink link) {
        String createdByUsername = userService.getUserById(link.getCreatedById()).getUsername();

        return new ScenarioInviteLinkDto(
                link.getId(),
                link.getScenarioId(),
                link.getToken(),
                link.getRole().name(),
                createdByUsername,
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.isActive(),
                link.getMaxUses(),
                link.getUseCount()
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Scenario requireScenario(Long id) {
        return scenarioRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Scenario not found"));
    }

    private Long requireOwner(Scenario scenario, Authentication authentication) {
        requireAuthenticated(authentication);
        Long userId = userService.getUserByUsername(authentication.getName()).getId();
        if (!userId.equals(scenario.getAuthor_id())) {
            throw new AccessDeniedException("Only the scenario owner can perform this action");
        }
        return userId;
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
    }
}
