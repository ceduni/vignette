package org.titiplex.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.titiplex.config.GlottologProperties;
import org.titiplex.persistence.model.User;
import org.titiplex.service.GlottologAdminService;
import org.titiplex.service.GlottologPythonBootstrapService;
import org.titiplex.service.RolesService;
import org.titiplex.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BootstrapDataTest {

    @Mock
    private RolesService rolesService;
    @Mock
    private UserService userService;
    @Mock
    private GlottologProperties glottologProperties;
    @Mock
    private GlottologPythonBootstrapService pythonBootstrapService;
    @Mock
    private GlottologAdminService glottologAdminService;

    @InjectMocks
    private BootstrapData bootstrapData;

    @BeforeEach
    void enableAdminBootstrap() {
        ReflectionTestUtils.setField(bootstrapData, "bootstrapAdminEnabled", true);
        ReflectionTestUtils.setField(bootstrapData, "bootstrapAdminUsername", " admin ");
        ReflectionTestUtils.setField(bootstrapData, "bootstrapAdminEmail", "admin@local.dev");
        ReflectionTestUtils.setField(bootstrapData, "bootstrapAdminPassword", "secret123");
        when(glottologProperties.isBootstrapImportEnabled()).thenReturn(false);
    }

    @Test
    void createsAdminThroughTheNormalRegistration() throws Exception {
        User created = new User();
        when(userService.getUserByUsername("admin")).thenReturn(null);
        when(userService.register("admin", "admin@local.dev", "secret123")).thenReturn(created);

        bootstrapData.run(null);

        verify(userService).register("admin", "admin@local.dev", "secret123");
        verify(userService).grantAdminRole(created);
    }

    @Test
    void promotesExistingUserInsteadOfCreatingOne() throws Exception {
        User existing = new User();
        when(userService.getUserByUsername("admin")).thenReturn(existing);

        bootstrapData.run(null);

        verify(userService).grantAdminRole(existing);
        verify(userService, never()).register(any(), any(), any());
    }

    @Test
    void skipsQuietlyWhenEmailAlreadyUsed() throws Exception {
        when(userService.getUserByUsername("admin")).thenReturn(null);
        when(userService.register("admin", "admin@local.dev", "secret123"))
                .thenThrow(new IllegalStateException("email already used"));

        bootstrapData.run(null); // must not crash the application startup

        verify(userService, never()).grantAdminRole(any());
    }
}
