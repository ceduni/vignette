package org.titiplex.api.security;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenApiAccessCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiAccess annotation = findAccess(handlerMethod);
        AccessSpec access = annotation == null ? AccessSpec.DEFAULT_PUBLIC : AccessSpec.of(annotation);

        applySecurity(operation, access);
        applyDescription(operation, access);
        applyResponses(operation, access);
        applyExtensions(operation, access);

        return operation;
    }

    private ApiAccess findAccess(HandlerMethod handlerMethod) {
        ApiAccess methodAnn = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiAccess.class);
        if (methodAnn != null) {
            return methodAnn;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiAccess.class);
    }

    private void applySecurity(Operation operation, AccessSpec access) {
        if (access.level() == ApiAccessLevel.PUBLIC) {
            operation.setSecurity(java.util.List.of());
            return;
        }

        if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
        }
    }

    private void applyDescription(Operation operation, AccessSpec access) {
        String prefix = switch (access.level()) {
            case PUBLIC -> "Access: Public endpoint.";
            case AUTHENTICATED -> "Access: Authenticated users only.";
            case USER -> "Access: Authenticated users with role USER only.";
            case ADMIN -> "Access: Authenticated users with role ADMIN only.";
            case OWNER_OR_ADMIN -> "Access: Resource owner or ADMIN only.";
        };

        String rule = access.rule() == null ? "" : access.rule().trim();
        String original = operation.getDescription() == null ? "" : operation.getDescription().trim();

        StringBuilder out = new StringBuilder(prefix);
        if (!rule.isBlank() && !rule.equals(prefix)) {
            out.append("\n\n").append(rule);
        }
        if (!original.isBlank()) {
            out.append("\n\n").append(original);
        }

        operation.setDescription(out.toString());
    }

    private void applyResponses(Operation operation, AccessSpec access) {
        if (operation.getResponses() == null) {
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
        }

        if (access.level() != ApiAccessLevel.PUBLIC && operation.getResponses().get("401") == null) {
            operation.getResponses().addApiResponse("401",
                    new ApiResponse().description("Authentication required"));
        }

        if ((access.level() == ApiAccessLevel.USER
                || access.level() == ApiAccessLevel.ADMIN
                || access.level() == ApiAccessLevel.OWNER_OR_ADMIN)
                && operation.getResponses().get("403") == null) {
            operation.getResponses().addApiResponse("403",
                    new ApiResponse().description("Authenticated but not allowed for this operation"));
        }
    }

    private void applyExtensions(Operation operation, AccessSpec access) {
        Map<String, Object> ext = operation.getExtensions();
        if (ext == null) {
            ext = new LinkedHashMap<>();
            operation.setExtensions(ext);
        }

        ext.put("x-access-level", access.level().name());
        if (!access.rule().isBlank()) {
            ext.put("x-authorization-rule", access.rule());
        }
        if (!access.ownerResource().isBlank()) {
            ext.put("x-owner-resource", access.ownerResource());
        }
    }

    private record AccessSpec(ApiAccessLevel level, String rule, String ownerResource) {
        static final AccessSpec DEFAULT_PUBLIC = new AccessSpec(ApiAccessLevel.PUBLIC, "Public endpoint.", "");

        static AccessSpec of(ApiAccess annotation) {
            return new AccessSpec(annotation.level(), annotation.rule(), annotation.ownerResource());
        }
    }
}
