package org.titiplex.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
    }
}
