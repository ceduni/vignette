alter table scenario
    add column if not exists active_background_audio_id bigint;

update scenario s
set active_background_audio_id = (
    select a.id
    from audio a
    where a.scenario_id = s.id
      and a.scope = 'BACKGROUND'
    order by a.idx, a.id
    limit 1
)
where s.active_background_audio_id is null
  and exists (
      select 1
      from audio a
      where a.scenario_id = s.id
        and a.scope = 'BACKGROUND'
  );
