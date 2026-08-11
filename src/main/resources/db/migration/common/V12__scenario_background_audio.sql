alter table audio
    add column if not exists scope varchar(32) default 'SCENE';

update audio
    set scope = 'SCENE'
    where scope is null;

alter table audio
    alter column scope set default 'SCENE';

alter table audio
    alter column scope set not null;

alter table audio
    alter column thumbnail_id drop not null;

alter table audio
    add column if not exists source_label varchar(180);

alter table audio
    add column if not exists source_url varchar(512);

create index if not exists idx_audio_scenario_scope
    on audio (scenario_id, scope, idx);
