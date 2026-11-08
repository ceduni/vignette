package org.titiplex.api.security;

import java.lang.annotation.*;

/**
 * <b>Documentation only.</b> Describes, in the generated OpenAPI/Swagger docs, that an endpoint
 * is restricted to the scenario's owner (or editors) and admins.
 * <p>
 * This annotation does <b>not</b> enforce anything. Access is enforced in a single place,
 * {@code ScenarioService.assertCan…} (edit, delete, publish, view), which every annotated endpoint
 * must call before changing anything. Keep {@link #resource()} in sync with the rule that is
 * actually applied.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OwnerOrAdminOperation {

    ProtectedResource resource();

    /**
     * Name of the method parameter that contains the protected resource id.
     * Example: "id", "scenarioId", "thumbnailId", "audioId"
     */
    String param() default "id";

    /**
     * Optional custom access description for documentation.
     * If blank, a default one is generated.
     */
    String description() default "";
}