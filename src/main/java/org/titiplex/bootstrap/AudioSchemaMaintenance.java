package org.titiplex.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AudioSchemaMaintenance implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public AudioSchemaMaintenance(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("alter table audio add column if not exists scope varchar(32) default 'SCENE'");
        jdbc.update("update audio set scope = 'SCENE' where scope is null");
        jdbc.execute("alter table audio alter column scope set default 'SCENE'");
        jdbc.execute("alter table audio alter column scope set not null");
        jdbc.execute("alter table audio alter column thumbnail_id drop not null");
        jdbc.execute("alter table audio add column if not exists source_label varchar(180)");
        jdbc.execute("alter table audio add column if not exists source_url varchar(512)");
        jdbc.execute("create index if not exists idx_audio_scenario_scope on audio (scenario_id, scope, idx)");

        List<String> constraints;
        try {
            constraints = jdbc.queryForList(
                    """
                    select tc.constraint_name
                    from INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                    where upper(tc.table_name) = 'AUDIO'
                      and tc.constraint_type = 'UNIQUE'
                      and (
                          (
                              (
                                  select count(*)
                                  from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                                  where kcu.constraint_name = tc.constraint_name
                                    and upper(kcu.table_name) = 'AUDIO'
                              ) = 2
                              and exists (
                                  select 1
                                  from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                                  where kcu.constraint_name = tc.constraint_name
                                    and upper(kcu.table_name) = 'AUDIO'
                                    and upper(kcu.column_name) = 'SCENARIO_ID'
                              )
                              and exists (
                                  select 1
                                  from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                                  where kcu.constraint_name = tc.constraint_name
                                    and upper(kcu.table_name) = 'AUDIO'
                                    and upper(kcu.column_name) = 'AUDIO_SHA256'
                              )
                          )
                          or (
                              (
                                  select count(*)
                                  from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                                  where kcu.constraint_name = tc.constraint_name
                                    and upper(kcu.table_name) = 'AUDIO'
                              ) = 1
                              and exists (
                                  select 1
                                  from INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                                  where kcu.constraint_name = tc.constraint_name
                                    and upper(kcu.table_name) = 'AUDIO'
                                    and upper(kcu.column_name) = 'THUMBNAIL_ID'
                              )
                          )
                      )
                    """,
                    String.class
            );
        } catch (BadSqlGrammarException e) {
            return;
        }

        for (String name : constraints) {
            jdbc.execute("alter table audio drop constraint if exists " + name);
        }
    }
}
