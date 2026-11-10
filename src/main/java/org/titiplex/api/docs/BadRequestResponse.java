package org.titiplex.api.docs;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.titiplex.api.dto.ApiError;

import java.lang.annotation.*;

/** OpenAPI documentation only: the endpoint can answer 400 (Invalid request) with an {@link ApiError} body. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ApiError.class))
)
public @interface BadRequestResponse {
}
