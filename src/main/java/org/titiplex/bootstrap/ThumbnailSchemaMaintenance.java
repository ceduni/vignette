package org.titiplex.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ThumbnailSchemaMaintenance implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public ThumbnailSchemaMaintenance(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> constraints;
        try {
            constraints = jdbc.queryForList(
                    """
                    select tc.constraint_name
                    from INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                    where upper(tc.table_name) = 'THUMBNAIL'
                      and tc.constraint_type = 'UNIQUE'
                      and (
                          select count(*)
                          from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                          where kcu.constraint_name = tc.constraint_name
                            and upper(kcu.table_name) = 'THUMBNAIL'
                      ) = 2
                      and exists (
                          select 1
                          from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                          where kcu.constraint_name = tc.constraint_name
                            and upper(kcu.table_name) = 'THUMBNAIL'
                            and upper(kcu.column_name) = 'SCENARIO_ID'
                      )
                      and (
                          exists (
                              select 1
                              from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                              where kcu.constraint_name = tc.constraint_name
                                and upper(kcu.table_name) = 'THUMBNAIL'
                                and upper(kcu.column_name) = 'IMAGE_SHA256'
                          )
                          or exists (
                              select 1
                              from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                              where kcu.constraint_name = tc.constraint_name
                                and upper(kcu.table_name) = 'THUMBNAIL'
                                and upper(kcu.column_name) = 'TITLE'
                          )
                      )
                    """,
                    String.class
            );
        } catch (BadSqlGrammarException e) {
            return;
        }

        for (String name : constraints) {
            jdbc.execute("alter table thumbnail drop constraint if exists " + name);
        }
    }
}
