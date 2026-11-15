package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.titiplex.api.dto.CreateAccreditationRequestBody;
import org.titiplex.api.dto.CreateDiscussionMessageRequest;
import org.titiplex.api.dto.DiscussionMessageDto;
import org.titiplex.api.dto.GrantAccreditationBody;
import org.titiplex.persistence.model.*;
import org.titiplex.service.CommunityService;
import org.titiplex.service.UserService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityApiControllerTest {

    @Mock
    private CommunityService communityService;

    @Mock
    private UserService userService;

    @InjectMocks
    private CommunityApiController controller;

    @Test
    void listDiscussions_mapsDiscussionMessagesToDtos() {
        User author = new User();
        author.setId(12L);
        author.setUsername("alice");

        DiscussionMessage message = new DiscussionMessage();
        message.setId(1L);
        message.setTargetType(DiscussionTargetType.LANGUAGE);
        message.setTargetId("chuj");
        message.setParentMessageId(null);
        message.setAuthorId(12L);
        message.setAuthor(author);
        message.setContributionType(ContributionType.GLOSS);
        message.setContent("Interesting glossing issue");
        message.setCreatedAt(Instant.parse("2026-03-20T10:15:30Z"));

        when(communityService.listMessages(DiscussionTargetType.LANGUAGE, "chuj"))
                .thenReturn(List.of(message));

        List<DiscussionMessageDto> result = controller.listDiscussions(
                DiscussionTargetType.LANGUAGE,
                "chuj"
        );

        assertEquals(1, result.size());
        DiscussionMessageDto dto = result.get(0);
        assertEquals(1L, dto.id());
        assertEquals("alice", dto.authorUsername());
        assertEquals(ContributionType.GLOSS, dto.contributionType());
        assertEquals("Interesting glossing issue", dto.content());
    }

    @Test
    void createDiscussion_usesAuthenticatedUserAndDelegatesToService() {
        Authentication auth = auth("alice", "ROLE_USER");

        User currentUser = new User();
        currentUser.setId(12L);
        currentUser.setUsername("alice");

        User author = new User();
        author.setId(12L);
        author.setUsername("alice");

        DiscussionMessage created = new DiscussionMessage();
        created.setId(8L);
        created.setTargetType(DiscussionTargetType.LANGUAGE);
        created.setTargetId("chuj");
        created.setAuthorId(12L);
        created.setAuthor(author);
        created.setContributionType(ContributionType.GENERAL);
        created.setContent("Hello");
        created.setCreatedAt(Instant.parse("2026-03-20T10:15:30Z"));

        when(userService.getUserByUsername("alice")).thenReturn(currentUser);
        when(communityService.createMessage(
                12L,
                DiscussionTargetType.LANGUAGE,
                "chuj",
                null,
                null,
                "Hello"
        )).thenReturn(created);

        DiscussionMessageDto dto = controller.createDiscussion(
                new CreateDiscussionMessageRequest(
                        DiscussionTargetType.LANGUAGE,
                        "chuj",
                        null,
                        null,
                        "Hello"
                ),
                auth
        );

        assertEquals(8L, dto.id());
        assertEquals(12L, dto.authorId());
        assertEquals("alice", dto.authorUsername());

        verify(communityService).createMessage(
                12L,
                DiscussionTargetType.LANGUAGE,
                "chuj",
                null,
                null,
                "Hello"
        );
    }

    @Test
    void createAccreditationRequest_usesPermissionScopeAndTargetId() {
        Authentication auth = auth("alice", "ROLE_USER");

        User requester = new User();
        requester.setId(12L);
        requester.setUsername("alice");

        AccreditationRequest created = new AccreditationRequest();
        created.setId(4L);
        created.setRequestedByUserId(12L);
        created.setRequester(requester);
        created.setPermissionType(AccreditationPermissionType.LANGUAGE_EDIT);
        created.setScopeType(AccreditationScopeType.LANGUAGE);
        created.setTargetId("chuj");
        created.setMotivation("I work on this language.");
        created.setStatus(AccreditationRequestStatus.PENDING);
        created.setCreatedAt(Instant.parse("2026-03-21T09:00:00Z"));

        when(userService.getUserByUsername("alice")).thenReturn(requester);
        when(communityService.createRequest(
                12L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.LANGUAGE,
                "chuj",
                "I work on this language."
        )).thenReturn(created);

        var dto = controller.createAccreditationRequest(
                new CreateAccreditationRequestBody(
                        AccreditationPermissionType.LANGUAGE_EDIT,
                        AccreditationScopeType.LANGUAGE,
                        "chuj",
                        "I work on this language."
                ),
                auth
        );

        assertEquals(4L, dto.id());
        assertEquals(AccreditationPermissionType.LANGUAGE_EDIT, dto.permissionType());
        assertEquals(AccreditationScopeType.LANGUAGE, dto.scopeType());
        assertEquals("chuj", dto.targetId());

        verify(communityService).createRequest(
                12L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.LANGUAGE,
                "chuj",
                "I work on this language."
        );
    }

    @Test
    void listAccreditationRequests_allowsScenarioOwner() {
        Authentication auth = auth("owner", "ROLE_USER");

        User requester = new User();
        requester.setId(17L);
        requester.setUsername("bob");

        AccreditationRequest request = new AccreditationRequest();
        request.setId(4L);
        request.setRequestedByUserId(17L);
        request.setRequester(requester);
        request.setPermissionType(AccreditationPermissionType.COMMUNITY_REVIEW);
        request.setScopeType(AccreditationScopeType.SCENARIO);
        request.setTargetId("9");
        request.setMotivation("I can help");
        request.setStatus(AccreditationRequestStatus.PENDING);
        request.setCreatedAt(Instant.parse("2026-03-21T09:00:00Z"));

        when(communityService.listRequests(
                AccreditationPermissionType.COMMUNITY_REVIEW,
                AccreditationScopeType.SCENARIO,
                "9"
        )).thenReturn(List.of(request));

        var result = controller.listAccreditationRequests(
                AccreditationPermissionType.COMMUNITY_REVIEW,
                AccreditationScopeType.SCENARIO,
                "9",
                auth
        );

        assertEquals(1, result.size());
        assertEquals("bob", result.get(0).requestedByUsername());
        assertEquals("I can help", result.get(0).motivation());
        assertEquals("9", result.get(0).targetId());
    }

    @Test
    void listAccreditationRequests_forbidsNonOwnerNonAdmin() {
        Authentication auth = auth("random-user", "ROLE_USER");
        // The rule itself is tested in CommunityServiceTest; here: the controller asks it and stops.
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException(
                        "Not allowed to read these accreditation requests"))
                .when(communityService).assertCanManageAccreditations(
                        AccreditationScopeType.SCENARIO, "9", auth, "Not allowed to read these accreditation requests");

        var ex = assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> controller.listAccreditationRequests(
                        AccreditationPermissionType.COMMUNITY_REVIEW,
                        AccreditationScopeType.SCENARIO,
                        "9",
                        auth
                )
        );

        assertEquals("Not allowed to read these accreditation requests", ex.getMessage());
        org.mockito.Mockito.verify(communityService, org.mockito.Mockito.never()).listRequests(any(), any(), any());
    }

    @Test
    void grantAccreditation_allowsAdminForGlobalScope() {
        Authentication auth = auth("root", "ROLE_ADMIN");

        User granter = new User();
        granter.setId(1L);
        granter.setUsername("root");

        User accreditedUser = new User();
        accreditedUser.setId(7L);
        accreditedUser.setUsername("bob");

        CommunityAccreditation created = new CommunityAccreditation();
        created.setId(22L);
        created.setUserId(7L);
        created.setUser(accreditedUser);
        created.setPermissionType(AccreditationPermissionType.LANGUAGE_EDIT);
        created.setScopeType(AccreditationScopeType.GLOBAL);
        created.setTargetId(null);
        created.setGrantedByUserId(1L);
        created.setGrantedAt(Instant.parse("2026-03-22T08:00:00Z"));
        created.setNote("Granted");

        when(userService.getUserByUsername("root")).thenReturn(granter);
        when(communityService.grantAccreditation(
                7L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.GLOBAL,
                null,
                1L,
                "Granted"
        )).thenReturn(created);

        var result = controller.grantAccreditation(
                new GrantAccreditationBody(
                        7L,
                        AccreditationPermissionType.LANGUAGE_EDIT,
                        AccreditationScopeType.GLOBAL,
                        null,
                        "Granted"
                ),
                auth
        );

        assertEquals(22L, result.id());
        assertEquals("bob", result.username());
        assertEquals(AccreditationPermissionType.LANGUAGE_EDIT, result.permissionType());
        assertEquals(AccreditationScopeType.GLOBAL, result.scopeType());

        verify(communityService).grantAccreditation(
                7L,
                AccreditationPermissionType.LANGUAGE_EDIT,
                AccreditationScopeType.GLOBAL,
                null,
                1L,
                "Granted"
        );
    }

    private Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "N/A",
                Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }
}