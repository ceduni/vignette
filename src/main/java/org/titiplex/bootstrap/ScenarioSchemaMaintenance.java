package org.titiplex.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScenarioSchemaMaintenance implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public ScenarioSchemaMaintenance(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Older schema versions had a global UNIQUE constraint on scenario.title alone,
        // which blocked different authors from ever reusing a title. It has been replaced
        // by a per-author/per-language composite constraint declared on the entity; drop
        // the stale single-column one here since ddl-auto=update never removes constraints.
        List<String> staleConstraints;
        try {
            staleConstraints = jdbc.queryForList(
                    """
                    select tc.constraint_name
                    from INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                    where upper(tc.table_name) = 'SCENARIO'
                      and tc.constraint_type = 'UNIQUE'
                      and (
                          select count(*)
                          from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                          where kcu.constraint_name = tc.constraint_name
                            and upper(kcu.table_name) = 'SCENARIO'
                      ) = 1
                      and exists (
                          select 1
                          from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                          where kcu.constraint_name = tc.constraint_name
                            and upper(kcu.table_name) = 'SCENARIO'
                            and upper(kcu.column_name) = 'TITLE'
                      )
                    """,
                    String.class
            );
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            // information_schema layout can vary across H2 modes/versions; skip rather than fail startup.
            return;
        }
        for (String name : staleConstraints) {
            jdbc.execute("alter table scenario drop constraint if exists " + name);
        }
    }
}
