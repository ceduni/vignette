package org.titiplex.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.UserRepository;
import org.titiplex.config.GlottologProperties;
import org.titiplex.service.LanguageImportService;
import org.titiplex.service.RolesService;

@Component
public class BootstrapData implements ApplicationRunner {

    private final LanguageImportService importService;
    private final RolesService rolesService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GlottologProperties glottologProperties;

    @Value("${app.bootstrap.admin.enabled:false}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.bootstrap.admin.username:}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap.admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String bootstrapAdminPassword;

    public BootstrapData(
            LanguageImportService importService,
            RolesService rolesService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            GlottologProperties glottologProperties
    ) {
        this.importService = importService;
        this.rolesService = rolesService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.glottologProperties = glottologProperties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        rolesService.loadRoles();
        bootstrapAdminAccount();

        if (!glottologProperties.isBootstrapImportEnabled()) {
            System.out.println("Classpath language bootstrap disabled (Python pipeline owns initial import).");
            return;
        }

        int inserted = importService.importIfEmptyFromClasspath();
        if (inserted > 0) {
            System.out.println("Imported " + inserted + " languages.");
        } else {
            System.out.println("Languages already present, skipping import.");
        }
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
