package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.titiplex.persistence.model.*;
import org.titiplex.persistence.repo.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private DiscussionMessageRepository discussionRepo;

    @Mock
    private AccreditationRequestRepository requestRepo;

    @Mock
    private CommunityAccreditationRepository accreditationRepo;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private ScenarioRepository scenarioRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CommunityService service;

    @Test
    void createMessage_defaultsContributionTypeToGeneral() {
        when(userRepository.existsById(12L)).thenReturn(true);
        when(languageRepository.existsById("chuj")).thenReturn(true);
        when(discussionRepo.save(any(DiscussionMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DiscussionMessage created = service.createMessage(
                12L,
                DiscussionTargetType.LANGUAGE,
                "chuj",
                null,
                null,
                "  Hello community  "
        );

        assertEquals(12L, created.getAuthorId());
        assertEquals(DiscussionTargetType.LANGUAGE, created.getTargetType());
        assertEquals("chuj", created.getTargetId());
        assertEquals("Hello community", created.getContent());
        assertEquals(ContributionType.GENERAL, created.getContributionType());
        verify(discussionRepo).save(any(DiscussionMessage.class));
    }

    @Test
    void createMessage_rejectsBlankContent() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createMessage(
                        12L,
                        DiscussionTargetType.LANGUAGE,
                        "chuj",
                        null,
                        ContributionType.GLOSS,
                        "   "
                )
        );

        assertEquals("Message content is required", ex.getMessage());
        verify(discussionRepo, never()).save(any());
    }

    @Test
    void createMessage_rejectsUnknownUser() {
        when(userRepository.existsById(12L)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createMessage(
                        12L,
                        DiscussionTargetType.LANGUAGE,
                        "chuj",
                        null,
                        ContributionType.GENERAL,
                        "Hello"
                )
        );

        assertEquals("Unknown user", ex.getMessage());
        verify(discussionRepo, never()).save(any());
    }

    @Test
    void createMessage_rejectsUnknownParentMessage() {
        when(userRepository.existsById(12L)).thenReturn(true);
        when(languageRepository.existsById("chuj")).thenReturn(true);
        when(discussionRepo.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createMessage(
                        12L,
                        DiscussionTargetType.LANGUAGE,
                        "chuj",
                        99L,
                        ContributionType.GENERAL,
                        "Reply"
                )
        );

        assertEquals("Unknown parent message", ex.getMessage());
        verify(discussionRepo, never()).save(any());
    }

    @Test
    void createMessage_rejectsParentFromDifferentDiscussion() {
        when(userRepository.existsById(12L)).thenReturn(true);
        when(languageRepository.existsById("chuj")).thenReturn(true);

        DiscussionMessage parent = new DiscussionMessage();
        parent.setId(44L);
        parent.setTargetType(DiscussionTargetType.AUDIO);
        parent.setTargetId("7");

        when(discussionRepo.findById(44L)).thenReturn(Optional.of(parent));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createMessage(
                        12L,
                        DiscussionTargetType.LANGUAGE,
                        "chuj",
                        44L,
                        ContributionType.GENERAL,
                        "Reply"
                )
        );

        assertEquals("Parent message belongs to a different discussion", ex.getMessage());
        verify(discussionRepo, never()).save(any());
    }

    @Test
    void listMessages_rejectsNonNumericAudioTargetId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.listMessages(DiscussionTargetType.AUDIO, "not-a-number")
        );

        assertEquals("Audio targetId must be numeric", ex.getMessage());
        verify(discussionRepo, never()).findByTargetTypeAndTargetIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void createRequest_rejectsDuplicatePendingRequest() {
        when(userRepository.existsById(12L)).thenReturn(true);
        when(languageRepository.existsById("chuj")).thenReturn(true);
        when(requestRepo.existsByRequestedByUserIdAndPermissionTypeAndScopeTypeAndTargetIdAndStatus(
                12L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.LANGUAGE,
                "chuj",
                AccreditationRequestStatus.PENDING
        )).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRequest(
                        12L,
                        AccreditationPermissionType.LANGUAGE_EDIT,
                        AccreditationScopeType.LANGUAGE,
                        "chuj",
                        "I want to contribute"
                )
        );

        assertEquals("A pending request already exists", ex.getMessage());
        verify(requestRepo, never()).save(any());
    }

    @Test
    void createRequest_rejectsMissingTargetIdForLanguageScope() {
        when(userRepository.existsById(12L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRequest(
                        12L,
                        AccreditationPermissionType.LANGUAGE_EDIT,
                        AccreditationScopeType.LANGUAGE,
                        null,
                        "I want to contribute"
                )
        );

        assertEquals("targetId is required for language scope", ex.getMessage());
        verify(requestRepo, never()).save(any());
    }

    @Test
    void createRequest_rejectsUnknownLanguageForLanguageScope() {
        when(userRepository.existsById(12L)).thenReturn(true);
        when(languageRepository.existsById("unknown-language")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRequest(
                        12L,
                        AccreditationPermissionType.LANGUAGE_EDIT,
                        AccreditationScopeType.LANGUAGE,
                        "unknown-language",
                        "I want to contribute"
                )
        );

        assertEquals("Unknown language", ex.getMessage());
        verify(requestRepo, never()).save(any());
    }

    @Test
    void createRequest_acceptsLanguageFamilyScopeWhenTargetIsFamily() {
        when(userRepository.existsById(12L)).thenReturn(true);

        Language family = new Language();
        family.setId("mayan");
        family.setLevel("family");

        when(languageRepository.findById("mayan")).thenReturn(Optional.of(family));
        when(requestRepo.existsByRequestedByUserIdAndPermissionTypeAndScopeTypeAndTargetIdAndStatus(
                12L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.LANGUAGE_FAMILY,
                "mayan",
                AccreditationRequestStatus.PENDING
        )).thenReturn(false);
        when(requestRepo.save(any(AccreditationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccreditationRequest created = service.createRequest(
                12L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.LANGUAGE_FAMILY,
                "mayan",
                "I specialize in Mayan languages"
        );

        assertEquals(12L, created.getRequestedByUserId());
        assertEquals(AccreditationPermissionType.LANGUAGE_EDIT, created.getPermissionType());
        assertEquals(AccreditationScopeType.LANGUAGE_FAMILY, created.getScopeType());
        assertEquals("mayan", created.getTargetId());
        assertEquals("I specialize in Mayan languages", created.getMotivation());
        assertEquals(AccreditationRequestStatus.PENDING, created.getStatus());
        verify(requestRepo).save(any(AccreditationRequest.class));
    }

    @Test
    void createRequest_rejectsLanguageFamilyScopeWhenTargetIsNotFamily() {
        when(userRepository.existsById(12L)).thenReturn(true);

        Language language = new Language();
        language.setId("chuj");
        language.setLevel("language");

        when(languageRepository.findById("chuj")).thenReturn(Optional.of(language));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRequest(
                        12L,
                        AccreditationPermissionType.LANGUAGE_EDIT,
                        AccreditationScopeType.LANGUAGE_FAMILY,
                        "chuj",
                        "I want to contribute"
                )
        );

        assertEquals("targetId must reference a language family", ex.getMessage());
        verify(requestRepo, never()).save(any());
    }

    @Test
    void grantAccreditation_returnsExistingGrantWithoutSavingAgain() {
        when(userRepository.existsById(9L)).thenReturn(true);

        CommunityAccreditation existing = new CommunityAccreditation();
        existing.setId(101L);
        existing.setUserId(9L);
        existing.setPermissionType(AccreditationPermissionType.LANGUAGE_EDIT);
        existing.setScopeType(AccreditationScopeType.GLOBAL);
        existing.setTargetId(null);

        when(accreditationRepo.findByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                9L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.GLOBAL,
                null
        )).thenReturn(Optional.of(existing));

        CommunityAccreditation result = service.grantAccreditation(
                9L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.GLOBAL,
                null,
                1L,
                "existing"
        );

        assertSame(existing, result);
        verify(accreditationRepo, never()).save(any());
    }

    @Test
    void grantAccreditation_savesNewLanguageScopedGrant() {
        when(userRepository.existsById(9L)).thenReturn(true);
        when(languageRepository.existsById("chuj")).thenReturn(true);
        when(accreditationRepo.findByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                9L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.LANGUAGE,
                "chuj"
        )).thenReturn(Optional.empty());
        when(accreditationRepo.save(any(CommunityAccreditation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CommunityAccreditation result = service.grantAccreditation(
                9L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.LANGUAGE,
                "chuj",
                1L,
                "Granted"
        );

        assertEquals(9L, result.getUserId());
        assertEquals(AccreditationPermissionType.LANGUAGE_EDIT, result.getPermissionType());
        assertEquals(AccreditationScopeType.LANGUAGE, result.getScopeType());
        assertEquals("chuj", result.getTargetId());
        assertEquals(1L, result.getGrantedByUserId());
        assertEquals("Granted", result.getNote());
        verify(accreditationRepo).save(any(CommunityAccreditation.class));
    }

    @Test
    void createRequest_acceptsScenarioEdit_onScenarioScope() {
        when(requestRepo.existsByRequestedByUserIdAndPermissionTypeAndScopeTypeAndTargetIdAndStatus(
                1L,
                AccreditationPermissionType.SCENARIO_EDIT,
                AccreditationScopeType.SCENARIO,
                "12",
                AccreditationRequestStatus.PENDING
        )).thenReturn(false);

        AccreditationRequest persisted = new AccreditationRequest();
        persisted.setId(10L);
        persisted.setRequestedByUserId(1L);
        persisted.setPermissionType(AccreditationPermissionType.SCENARIO_EDIT);
        persisted.setScopeType(AccreditationScopeType.SCENARIO);
        persisted.setTargetId("12");
        persisted.setMotivation("I can maintain this scenario.");
        persisted.setStatus(AccreditationRequestStatus.PENDING);
        persisted.setCreatedAt(Instant.now());

        when(requestRepo.save(any())).thenReturn(persisted);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(scenarioRepository.existsById(12L)).thenReturn(true);

        AccreditationRequest result = service.createRequest(
                1L,
                AccreditationPermissionType.SCENARIO_EDIT,
                AccreditationScopeType.SCENARIO,
                "12",
                "I can maintain this scenario."
        );

        assertNotNull(result);
        assertEquals(AccreditationPermissionType.SCENARIO_EDIT, result.getPermissionType());
        assertEquals(AccreditationScopeType.SCENARIO, result.getScopeType());
        assertEquals("12", result.getTargetId());
    }

    @Test
    void createRequest_acceptsScenarioModerate_onScenarioScope() {
        when(requestRepo.existsByRequestedByUserIdAndPermissionTypeAndScopeTypeAndTargetIdAndStatus(
                1L,
                AccreditationPermissionType.SCENARIO_MODERATE,
                AccreditationScopeType.SCENARIO,
                "12",
                AccreditationRequestStatus.PENDING
        )).thenReturn(false);

        AccreditationRequest persisted = new AccreditationRequest();
        persisted.setId(11L);
        persisted.setRequestedByUserId(1L);
        persisted.setPermissionType(AccreditationPermissionType.SCENARIO_MODERATE);
        persisted.setScopeType(AccreditationScopeType.SCENARIO);
        persisted.setTargetId("12");
        persisted.setMotivation("I can moderate this scenario.");
        persisted.setStatus(AccreditationRequestStatus.PENDING);
        persisted.setCreatedAt(Instant.now());

        when(requestRepo.save(any())).thenReturn(persisted);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(scenarioRepository.existsById(12L)).thenReturn(true);

        AccreditationRequest result = service.createRequest(
                1L,
                AccreditationPermissionType.SCENARIO_MODERATE,
                AccreditationScopeType.SCENARIO,
                "12",
                "I can moderate this scenario."
        );

        assertNotNull(result);
        assertEquals(AccreditationPermissionType.SCENARIO_MODERATE, result.getPermissionType());
    }

    @Test
    void createRequest_rejectsScenarioEdit_onGlobalScope() {
        when(userRepository.existsById(1L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRequest(
                        1L,
                        AccreditationPermissionType.SCENARIO_EDIT,
                        AccreditationScopeType.GLOBAL,
                        null,
                        "Invalid scope"
                )
        );

        assertEquals("SCENARIO_EDIT is only valid for SCENARIO scope", ex.getMessage());
    }

    @Test
    void createRequest_rejectsScenarioModerate_onLanguageScope() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(languageRepository.existsById("fra")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRequest(
                        1L,
                        AccreditationPermissionType.SCENARIO_MODERATE,
                        AccreditationScopeType.LANGUAGE,
                        "fra",
                        "Invalid scope"
                )
        );

        assertEquals("SCENARIO_MODERATE is only valid for SCENARIO scope", ex.getMessage());
    }

    @Test
    void createRequest_rejectsLanguageEdit_onScenarioScope() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(scenarioRepository.existsById(12L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createRequest(
                        1L,
                        AccreditationPermissionType.LANGUAGE_EDIT,
                        AccreditationScopeType.SCENARIO,
                        "12",
                        "Invalid scope"
                )
        );

        assertEquals(
                "LANGUAGE_EDIT is only valid for GLOBAL, LANGUAGE or LANGUAGE_FAMILY scope",
                ex.getMessage()
        );
    }

    @Test
    void grantAccreditation_acceptsScenarioEdit_onScenarioScope() {
        when(accreditationRepo.findByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                2L,
                AccreditationPermissionType.SCENARIO_EDIT,
                AccreditationScopeType.SCENARIO,
                "12"
        )).thenReturn(Optional.empty());

        CommunityAccreditation saved = new CommunityAccreditation();
        saved.setId(100L);
        saved.setUserId(2L);
        saved.setPermissionType(AccreditationPermissionType.SCENARIO_EDIT);
        saved.setScopeType(AccreditationScopeType.SCENARIO);
        saved.setTargetId("12");

        when(accreditationRepo.save(any())).thenReturn(saved);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(scenarioRepository.existsById(12L)).thenReturn(true);

        CommunityAccreditation result = service.grantAccreditation(
                2L,
                AccreditationPermissionType.SCENARIO_EDIT,
                AccreditationScopeType.SCENARIO,
                "12",
                1L,
                "Granted by admin"
        );

        assertEquals(AccreditationPermissionType.SCENARIO_EDIT, result.getPermissionType());
        assertEquals(AccreditationScopeType.SCENARIO, result.getScopeType());
    }

    @Test
    void grantAccreditation_rejectsScenarioModerate_onGlobalScope() {
        when(userRepository.existsById(2L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.grantAccreditation(
                        2L,
                        AccreditationPermissionType.SCENARIO_MODERATE,
                        AccreditationScopeType.GLOBAL,
                        null,
                        1L,
                        "Invalid"
                )
        );

        assertEquals("SCENARIO_MODERATE is only valid for SCENARIO scope", ex.getMessage());
    }

    @Test
    void listRequests_acceptsScenarioEdit_onScenarioScope() {
        when(requestRepo.findByPermissionTypeAndScopeTypeAndTargetIdOrderByCreatedAtDesc(
                AccreditationPermissionType.SCENARIO_EDIT,
                AccreditationScopeType.SCENARIO,
                "12"
        )).thenReturn(List.of());
        when(scenarioRepository.existsById(12L)).thenReturn(true);

        List<AccreditationRequest> result = service.listRequests(
                AccreditationPermissionType.SCENARIO_EDIT,
                AccreditationScopeType.SCENARIO,
                "12"
        );

        assertNotNull(result);
        verify(requestRepo).findByPermissionTypeAndScopeTypeAndTargetIdOrderByCreatedAtDesc(
                AccreditationPermissionType.SCENARIO_EDIT,
                AccreditationScopeType.SCENARIO,
                "12"
        );
    }

    @Test
    void listRequests_rejectsLanguageEdit_onScenarioScope() {
        when(scenarioRepository.existsById(12L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.listRequests(
                        AccreditationPermissionType.LANGUAGE_EDIT,
                        AccreditationScopeType.SCENARIO,
                        "12"
                )
        );

        assertEquals(
                "LANGUAGE_EDIT is only valid for GLOBAL, LANGUAGE or LANGUAGE_FAMILY scope",
                ex.getMessage()
        );
    }

    @Test
    void listAccreditations_acceptsScenarioModerate_onScenarioScope() {
        when(accreditationRepo.findByPermissionTypeAndScopeTypeAndTargetIdOrderByGrantedAtDesc(
                AccreditationPermissionType.SCENARIO_MODERATE,
                AccreditationScopeType.SCENARIO,
                "12"
        )).thenReturn(List.of());
        when(scenarioRepository.existsById(12L)).thenReturn(true);

        List<CommunityAccreditation> result = service.listAccreditations(
                AccreditationPermissionType.SCENARIO_MODERATE,
                AccreditationScopeType.SCENARIO,
                "12"
        );

        assertNotNull(result);
        verify(accreditationRepo).findByPermissionTypeAndScopeTypeAndTargetIdOrderByGrantedAtDesc(
                AccreditationPermissionType.SCENARIO_MODERATE,
                AccreditationScopeType.SCENARIO,
                "12"
        );
    }

    @Test
    void reviewRequest_approvedScenarioEdit_createsMatchingAccreditation() {
        AccreditationRequest pending = new AccreditationRequest();
        pending.setId(55L);
        pending.setRequestedByUserId(2L);
        pending.setPermissionType(AccreditationPermissionType.SCENARIO_EDIT);
        pending.setScopeType(AccreditationScopeType.SCENARIO);
        pending.setTargetId("12");
        pending.setStatus(AccreditationRequestStatus.PENDING);

        when(requestRepo.findById(55L)).thenReturn(Optional.of(pending));
        when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accreditationRepo.findByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                2L,
                AccreditationPermissionType.SCENARIO_EDIT,
                AccreditationScopeType.SCENARIO,
                "12"
        )).thenReturn(Optional.empty());
        when(accreditationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(scenarioRepository.existsById(12L)).thenReturn(true);

        AccreditationRequest reviewed = service.reviewRequest(55L, 1L, true, "Approved");

        assertEquals(AccreditationRequestStatus.APPROVED, reviewed.getStatus());

        ArgumentCaptor<CommunityAccreditation> captor = ArgumentCaptor.forClass(CommunityAccreditation.class);
        verify(accreditationRepo).save(captor.capture());

        CommunityAccreditation created = captor.getValue();
        assertEquals(2L, created.getUserId());
        assertEquals(AccreditationPermissionType.SCENARIO_EDIT, created.getPermissionType());
        assertEquals(AccreditationScopeType.SCENARIO, created.getScopeType());
        assertEquals("12", created.getTargetId());
    }
}