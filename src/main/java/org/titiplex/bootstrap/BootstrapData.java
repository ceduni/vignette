package org.titiplex.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.titiplex.config.GlottologProperties;
import org.titiplex.persistence.model.User;
import org.titiplex.service.GlottologAdminService;
import org.titiplex.service.GlottologPythonBootstrapService;
import org.titiplex.service.RolesService;
import org.titiplex.service.UserService;

@Component
public class BootstrapData implements ApplicationRunner {

    private final RolesService rolesService;
    private final UserService userService;
    private final GlottologProperties glottologProperties;
    private final GlottologPythonBootstrapService pythonBootstrapService;
    private final GlottologAdminService glottologAdminService;

    @Value("${app.bootstrap.admin.enabled:false}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.bootstrap.admin.username:}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap.admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String bootstrapAdminPassword;

    public BootstrapData(
            RolesService rolesService,
            UserService userService,
            GlottologProperties glottologProperties,
            GlottologPythonBootstrapService pythonBootstrapService,
            GlottologAdminService glottologAdminService
    ) {
        this.rolesService = rolesService;
        this.userService = userService;
        this.glottologProperties = glottologProperties;
        this.pythonBootstrapService = pythonBootstrapService;
        this.glottologAdminService = glottologAdminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        rolesService.loadRoles();
        bootstrapAdminAccount();

        if (!glottologProperties.isBootstrapImportEnabled()) {
            System.out.println(
                    "Language bootstrap at startup disabled "
                            + "(app.glottolog.bootstrap-import-enabled=false)."
            );
            return;
        }

        // Ensure glottolog_admin_settings exists for the Python worker.
        try {
            glottologAdminService.getSettings();
        } catch (Exception ex) {
            System.out.println(
                    "Could not init Glottolog admin settings before Python bootstrap: "
                            + ex.getMessage()
            );
        }

        // Empty DB only: run Python worker pipeline (Zenodo → PostgreSQL).
        pythonBootstrapService.bootstrapIfEmpty();
    }

    private void bootstrapAdminAccount() {
        if (!bootstrapAdminEnabled) {
            return;
        }

        String username = trimmedOrEmpty(bootstrapAdminUsername);
        String email = trimmedOrEmpty(bootstrapAdminEmail);
        String password = bootstrapAdminPassword == null ? "" : bootstrapAdminPassword;

        if (username.isEmpty() || email.isEmpty() || password.isBlank()) {
            System.out.println("Bootstrap admin skipped: missing username, email, or password.");
            return;
        }

        User existingUser = userService.getUserByUsername(username);
        if (existingUser != null) {
            if (userService.grantAdminRole(existingUser)) {
                System.out.println("Granted ROLE_ADMIN to existing bootstrap user: " + username);
            }
            return;
        }

        // Same creation path as a normal sign-up (normalization, uniqueness, password hashing),
        // then the admin role on top.
        try {
            User adminUser = userService.register(username, email, password);
            userService.grantAdminRole(adminUser);
            System.out.println("Created bootstrap admin user: " + username);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Bootstrap admin skipped: " + e.getMessage());
        }
    }

    private static String trimmedOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
