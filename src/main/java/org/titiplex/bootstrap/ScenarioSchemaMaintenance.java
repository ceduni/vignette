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
            return;
        }
        for (String name : staleConstraints) {
            jdbc.execute("alter table scenario drop constraint if exists " + name);
        }
        jdbc.execute("alter table scenario alter column description set data type varchar(500)");
    }
}
