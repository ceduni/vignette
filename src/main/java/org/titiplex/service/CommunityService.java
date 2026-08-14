package org.titiplex.service;

import org.springframework.stereotype.Service;
import org.titiplex.persistence.model.*;
import org.titiplex.persistence.repo.*;

import java.time.Instant;
import java.util.List;

@Service
public class CommunityService {
    private final DiscussionMessageRepository discussionRepo;
    private final AccreditationRequestRepository requestRepo;
    private final CommunityAccreditationRepository accreditationRepo;
    private final LanguageRepository languageRepository;
    private final AudioRepository audioRepository;
    private final ScenarioRepository scenarioRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public CommunityService(DiscussionMessageRepository discussionRepo,
                            AccreditationRequestRepository requestRepo,
                            CommunityAccreditationRepository accreditationRepo,
                            LanguageRepository languageRepository,
                            AudioRepository audioRepository,
                            ScenarioRepository scenarioRepository,
                            UserRepository userRepository,
                            NotificationService notificationService) {
        this.discussionRepo = discussionRepo;
        this.requestRepo = requestRepo;
        this.accreditationRepo = accreditationRepo;
        this.languageRepository = languageRepository;
        this.audioRepository = audioRepository;
        this.scenarioRepository = scenarioRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public List<DiscussionMessage> listMessages(DiscussionTargetType targetType, String targetId) {
        validateTarget(targetType, targetId);
        return discussionRepo.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId);
    }

    public DiscussionMessage createMessage(Long authorId,
                                           DiscussionTargetType targetType,
                                           String targetId,
                                           Long parentMessageId,
                                           ContributionType contributionType,
                                           String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message content is required");
        if (!userRepository.existsById(authorId)) throw new IllegalArgumentException("Unknown user");
        validateTarget(targetType, targetId);

        DiscussionMessage parent = null;
        if (parentMessageId != null) {
            parent = discussionRepo.findById(parentMessageId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown parent message"));

            boolean sameTargetType = parent.getTargetType() == targetType;
            boolean sameTargetId = targetId.equals(parent.getTargetId());

            if (!sameTargetType || !sameTargetId) {
                throw new IllegalArgumentException("Parent message belongs to a different discussion");
            }
        }

        DiscussionMessage message = new DiscussionMessage();
        message.setAuthorId(authorId);
        message.setTargetType(targetType);
        message.setTargetId(targetId);
        message.setParentMessageId(parentMessageId);
        message.setContributionType(contributionType == null ? ContributionType.GENERAL : contributionType);
        message.setContent(content.trim());
        message.setCreatedAt(Instant.now());
        DiscussionMessage saved = discussionRepo.save(message);

        notificationService.notifyDiscussionMessage(saved, parent);

        return saved;
    }

    public AccreditationRequest createRequest(Long requesterId,
                                              AccreditationPermissionType permissionType,
                                              AccreditationScopeType scopeType,
                                              String targetId,
                                              String motivation) {
        if (permissionType == null) throw new IllegalArgumentException("permissionType is required");
        if (motivation == null || motivation.isBlank()) throw new IllegalArgumentException("Motivation is required");
        if (!userRepository.existsById(requesterId)) throw new IllegalArgumentException("Unknown user");

        validateAccreditationTarget(scopeType, targetId);
        validatePermissionScope(permissionType, scopeType);

        boolean pendingExists = requestRepo.existsByRequestedByUserIdAndPermissionTypeAndScopeTypeAndTargetIdAndStatus(
                requesterId,
                permissionType,
                scopeType,
                normalizeTargetId(scopeType, targetId),
                AccreditationRequestStatus.PENDING
        );
        if (pendingExists) throw new IllegalArgumentException("A pending request already exists");

        AccreditationRequest request = new AccreditationRequest();
        request.setRequestedByUserId(requesterId);
        request.setPermissionType(permissionType);
        request.setScopeType(scopeType);
        request.setTargetId(normalizeTargetId(scopeType, targetId));
        request.setMotivation(motivation.trim());
        request.setStatus(AccreditationRequestStatus.PENDING);
        request.setCreatedAt(Instant.now());
        return requestRepo.save(request);
    }

    public List<AccreditationRequest> listRequests(AccreditationPermissionType permissionType,
                                                   AccreditationScopeType scopeType,
                                                   String targetId) {
        if (permissionType == null) throw new IllegalArgumentException("permissionType is required");
        validateAccreditationTarget(scopeType, targetId);
        validatePermissionScope(permissionType, scopeType);

        String normalizedTargetId = normalizeTargetId(scopeType, targetId);
        if (scopeType == AccreditationScopeType.GLOBAL) {
            return requestRepo.findByPermissionTypeAndScopeTypeOrderByCreatedAtDesc(permissionType, scopeType);
        }
        return requestRepo.findByPermissionTypeAndScopeTypeAndTargetIdOrderByCreatedAtDesc(
                permissionType, scopeType, normalizedTargetId
        );
    }

    public AccreditationRequest getRequest(Long requestId) {
        return requestRepo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("Unknown request"));
    }

    public AccreditationRequest reviewRequest(Long requestId, Long reviewerUserId, boolean approved, String reviewNote) {
        AccreditationRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request"));

        if (req.getStatus() != AccreditationRequestStatus.PENDING) {
            throw new IllegalArgumentException("Request already reviewed");
        }

        req.setReviewedByUserId(reviewerUserId);
        req.setReviewedAt(Instant.now());
        req.setReviewNote(reviewNote == null ? null : reviewNote.trim());
        req.setStatus(approved ? AccreditationRequestStatus.APPROVED : AccreditationRequestStatus.REJECTED);

        AccreditationRequest saved = requestRepo.save(req);
        if (approved) {
            grantAccreditation(
                    req.getRequestedByUserId(),
                    req.getPermissionType(),
                    req.getScopeType(),
                    req.getTargetId(),
                    reviewerUserId,
                    "Granted from request #" + req.getId()
            );
        }
        return saved;
    }

    public CommunityAccreditation grantAccreditation(Long userId,
                                                     AccreditationPermissionType permissionType,
                                                     AccreditationScopeType scopeType,
                                                     String targetId,
                                                     Long grantedByUserId,
                                                     String note) {
        if (permissionType == null) throw new IllegalArgumentException("permissionType is required");
        if (!userRepository.existsById(userId)) throw new IllegalArgumentException("Unknown user");

        validateAccreditationTarget(scopeType, targetId);
        validatePermissionScope(permissionType, scopeType);

        String normalizedTargetId = normalizeTargetId(scopeType, targetId);

        CommunityAccreditation existing = accreditationRepo
                .findByUserIdAndPermissionTypeAndScopeTypeAndTargetId(userId, permissionType, scopeType, normalizedTargetId)
                .orElse(null);
        if (existing != null) return existing;

        CommunityAccreditation grant = new CommunityAccreditation();
        grant.setUserId(userId);
        grant.setPermissionType(permissionType);
        grant.setScopeType(scopeType);
        grant.setTargetId(normalizedTargetId);
        grant.setGrantedByUserId(grantedByUserId);
        grant.setGrantedAt(Instant.now());
        grant.setNote(note == null ? null : note.trim());
        return accreditationRepo.save(grant);
    }

    public List<CommunityAccreditation> listAccreditations(AccreditationPermissionType permissionType,
                                                           AccreditationScopeType scopeType,
                                                           String targetId) {
        if (permissionType == null) throw new IllegalArgumentException("permissionType is required");
        validateAccreditationTarget(scopeType, targetId);
        validatePermissionScope(permissionType, scopeType);

        String normalizedTargetId = normalizeTargetId(scopeType, targetId);
        if (scopeType == AccreditationScopeType.GLOBAL) {
            return accreditationRepo.findByPermissionTypeAndScopeTypeOrderByGrantedAtDesc(permissionType, scopeType);
        }
        return accreditationRepo.findByPermissionTypeAndScopeTypeAndTargetIdOrderByGrantedAtDesc(
                permissionType, scopeType, normalizedTargetId
        );
    }

    public boolean isScenarioOwner(Long scenarioId, String username) {
        return scenarioRepository.existsByIdAndAuthorUsername(scenarioId, username);
    }

    private void validateTarget(DiscussionTargetType targetType, String targetId) {
        if (targetType == null) throw new IllegalArgumentException("targetType is required");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");

        boolean exists = switch (targetType) {
            case LANGUAGE -> languageRepository.existsById(targetId);
            case AUDIO -> {
                long audioId;
                try {
                    audioId = Long.parseLong(targetId);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Audio targetId must be numeric");
                }
                yield audioRepository.existsById(audioId);
            }
            case SCENARIO -> {       // ← ajoute ce cas
                long scenarioId;
                try {
                    scenarioId = Long.parseLong(targetId);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Scenario targetId must be numeric");
                }
                yield scenarioRepository.existsById(scenarioId);
            }
        };

        if (!exists) throw new IllegalArgumentException("Unknown target");
    }

    private void validateAccreditationTarget(AccreditationScopeType scopeType, String targetId) {
        if (scopeType == null) throw new IllegalArgumentException("scopeType is required");

        switch (scopeType) {
            case GLOBAL -> {
                if (targetId != null && !targetId.isBlank()) {
                    throw new IllegalArgumentException("targetId must be null for global scope");
                }
            }
            case SCENARIO -> {
                if (targetId == null || targetId.isBlank()) {
                    throw new IllegalArgumentException("targetId is required for scenario scope");
                }
                long scenarioId;
                try {
                    scenarioId = Long.parseLong(targetId);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("scenario targetId must be numeric");
                }
                if (!scenarioRepository.existsById(scenarioId)) {
                    throw new IllegalArgumentException("Unknown scenario");
                }
            }
            case LANGUAGE -> {
                if (targetId == null || targetId.isBlank()) {
                    throw new IllegalArgumentException("targetId is required for language scope");
                }
                if (!languageRepository.existsById(targetId)) {
                    throw new IllegalArgumentException("Unknown language");
                }
            }
            case LANGUAGE_FAMILY -> {
                if (targetId == null || targetId.isBlank()) {
                    throw new IllegalArgumentException("targetId is required for language family scope");
                }
                Language lang = languageRepository.findById(targetId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown language family"));
                if (lang.getLevel() == null || !lang.getLevel().equalsIgnoreCase("family")) {
                    throw new IllegalArgumentException("targetId must reference a language family");
                }
            }
        }
    }

    private String normalizeTargetId(AccreditationScopeType scopeType, String targetId) {
        return scopeType == AccreditationScopeType.GLOBAL ? null : targetId.trim();
    }

    private void validatePermissionScope(AccreditationPermissionType permissionType,
                                         AccreditationScopeType scopeType) {
        if (permissionType == null) {
            throw new IllegalArgumentException("permissionType is required");
        }
        if (scopeType == null) {
            throw new IllegalArgumentException("scopeType is required");
        }

        switch (permissionType) {
            case COMMUNITY_REVIEW -> {
                // allowed on all scopes for now
            }
            case LANGUAGE_EDIT -> {
                if (scopeType != AccreditationScopeType.GLOBAL
                        && scopeType != AccreditationScopeType.LANGUAGE
                        && scopeType != AccreditationScopeType.LANGUAGE_FAMILY) {
                    throw new IllegalArgumentException("LANGUAGE_EDIT is only valid for GLOBAL, LANGUAGE or LANGUAGE_FAMILY scope");
                }
            }
            case SCENARIO_EDIT, SCENARIO_MODERATE -> {
                if (scopeType != AccreditationScopeType.SCENARIO) {
                    throw new IllegalArgumentException(permissionType + " is only valid for SCENARIO scope");
                }
            }
        }
    }
}