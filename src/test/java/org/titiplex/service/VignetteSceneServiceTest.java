package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.titiplex.persistence.repo.VignetteSceneRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class VignetteSceneServiceTest {

    private final VignetteSceneService service = new VignetteSceneService(mock(VignetteSceneRepository.class));

    @Test
    void create_rejectsMissingSceneDataBeforeDatabaseSave() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(1L, "Scene", null)
        );

        assertEquals("Scene data is required", error.getMessage());
    }

    @Test
    void create_rejectsNamesThatExceedTheDatabaseLimit() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(1L, "x".repeat(201), "{}")
        );

        assertEquals("Scene name must be 200 characters or fewer", error.getMessage());
    }
}
