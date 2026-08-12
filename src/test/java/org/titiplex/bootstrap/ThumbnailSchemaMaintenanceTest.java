package org.titiplex.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ThumbnailSchemaMaintenanceTest {

    @Test
    void removesTheDuplicateImageConstraint() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(
                        "UK_THUMBNAIL_SCENARIO_SHA256",
                        "UK_THUMBNAIL_SCENARIO_TITLE"
                ));

        new ThumbnailSchemaMaintenance(jdbc).run(null);

        verify(jdbc).execute(
                "alter table thumbnail drop constraint if exists UK_THUMBNAIL_SCENARIO_SHA256"
        );
        verify(jdbc).execute(
                "alter table thumbnail drop constraint if exists UK_THUMBNAIL_SCENARIO_TITLE"
        );
    }
}
