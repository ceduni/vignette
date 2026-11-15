-- Objects that only the former Flyway migrations created (never Hibernate).
-- Idempotent on purpose: it runs both on fresh databases (after V1) and on older
-- databases that were baselined at V1 and may or may not already have them.

-- Lookup indexes ------------------------------------------------------------

create index if not exists idx_discussion_message_target
    on community.discussion_message (target_type, target_id, created_at);

create index if not exists idx_discussion_message_parent
    on community.discussion_message (parent_message_id);

create index if not exists idx_scenario_history_entry_scenario
    on public.scenario_history_entry (scenario_id, created_at desc);

create index if not exists idx_vignette_maker_scene_user
    on public.vignette_maker_scene (user_id);

create index if not exists idx_glottolog_update_history_started_at
    on public.glottolog_update_history (started_at desc);

create index if not exists idx_glottolog_update_request_status_requested
    on public.glottolog_update_request (status, requested_at);

create index if not exists idx_glottolog_notification_created
    on public.glottolog_notification (created_at desc);

-- Singleton row read by the Python Glottolog worker ---------------------------

insert into public.glottolog_pipeline_state (id, current_status, retry_count, updated_at)
values (1, 'IDLE', 0, current_timestamp)
on conflict (id) do nothing;

-- Wake the Python worker via LISTEN/NOTIFY ------------------------------------
-- Channel: glottolog_wake. Payloads: manual_request | config_changed.
-- The worker must use a session-level connection (not a transaction pooler).

create or replace function public.glottolog_notify_wake()
    returns trigger
    language plpgsql
as
$$
begin
    if tg_table_name = 'glottolog_update_request' then
        if tg_op = 'INSERT' and new.status = 'PENDING' then
            perform pg_notify('glottolog_wake', 'manual_request');
        elsif tg_op = 'UPDATE'
            and new.status = 'PENDING'
            and old.status is distinct from 'PENDING' then
            perform pg_notify('glottolog_wake', 'manual_request');
        end if;
    elsif tg_table_name = 'glottolog_admin_settings' then
        perform pg_notify('glottolog_wake', 'config_changed');
    end if;
    return new;
end;
$$;

drop trigger if exists trg_glottolog_request_wake on public.glottolog_update_request;
create trigger trg_glottolog_request_wake
    after insert or update
    on public.glottolog_update_request
    for each row
execute function public.glottolog_notify_wake();

drop trigger if exists trg_glottolog_settings_wake on public.glottolog_admin_settings;
create trigger trg_glottolog_settings_wake
    after update
    on public.glottolog_admin_settings
    for each row
execute function public.glottolog_notify_wake();
