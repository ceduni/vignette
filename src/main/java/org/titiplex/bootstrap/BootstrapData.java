package org.titiplex.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.titiplex.config.GlottologProperties;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.UserRepository;
import org.titiplex.service.GlottologAdminService;
import org.titiplex.service.GlottologPythonBootstrapService;
import org.titiplex.service.RolesService;

@Component
public class BootstrapData implements ApplicationRunner {

    private final RolesService rolesService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
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
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            GlottologProperties glottologProperties,
            GlottologPythonBootstrapService pythonBootstrapService,
            GlottologAdminService glottologAdminService
    ) {
        this.rolesService = rolesService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        User existingUser = userRepository.findByUsername(username).orElse(null);
        if (existingUser != null) {
            boolean isAdmin = existingUser.getRoles().stream()
                    .anyMatch(role -> "ROLE_ADMIN".equals(role.getName()));
            if (!isAdmin) {
                existingUser.getRoles().add(rolesService.getAdminRole());
                if (existingUser.getRoles().stream().noneMatch(role -> "ROLE_USER".equals(role.getName()))) {
                    existingUser.getRoles().add(rolesService.getUserRole());
                }
                userRepository.save(existingUser);
                System.out.println("Granted ROLE_ADMIN to existing bootstrap user: " + username);
            }
            return;
        }

        if (userRepository.existsByEmail(email)) {
            System.out.println("Bootstrap admin skipped: email already used by another account.");
            return;
        }

        User adminUser = new User();
        adminUser.setUsername(username);
        adminUser.setEmail(email);
        adminUser.setDisplayName(username);
        adminUser.setPasswordHash(passwordEncoder.encode(password));
        adminUser.getRoles().add(rolesService.getUserRole());
        adminUser.getRoles().add(rolesService.getAdminRole());

        userRepository.save(adminUser);
        System.out.println("Created bootstrap admin user: " + username);
    }

    private static String trimmedOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
