package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import org.titiplex.persistence.model.Role;
import org.titiplex.persistence.model.User;

import java.util.Set;
import java.util.stream.Collectors;

@Schema(name = "AdminUserRow", description = "User row for administration listing.")
public record AdminUserRowDto(
        @Schema(description = "User ID.", example = "5")
        Long id,

        @Schema(description = "Username.", example = "alice")
        String username,

        @Schema(description = "Email.", example = "alice@example.com")
        String email,

        @Schema(description = "Display name.", example = "Alice Martin")
        String displayName,

        @Schema(description = "Granted roles.")
        Set<String> roles,

        @Schema(description = "Public profile flag.", example = "true")
        boolean profilePublic
) {
    public static AdminUserRowDto from(User user) {
        return new AdminUserRowDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                user.isProfilePublic()
        );
    }
}
