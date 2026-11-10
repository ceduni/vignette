package org.titiplex.api.docs;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.titiplex.api.dto.ApiError;

import java.lang.annotation.*;

/** OpenAPI documentation only: the endpoint can answer 401 (Authentication required) with an {@link ApiError} body. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ApiError.class))
)
public @interface UnauthorizedResponse {
}
