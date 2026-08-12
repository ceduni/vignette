package org.titiplex.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioSchemaMaintenanceTest {

    @Test
    void removesTheDuplicateAudioConstraint() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("UK_AUDIO_SCENARIO_SHA256", "UK_AUDIO_THUMBNAIL"));

        new AudioSchemaMaintenance(jdbc).run(null);

        verify(jdbc).execute("alter table audio drop constraint if exists UK_AUDIO_SCENARIO_SHA256");
        verify(jdbc).execute("alter table audio drop constraint if exists UK_AUDIO_THUMBNAIL");
    }
}
