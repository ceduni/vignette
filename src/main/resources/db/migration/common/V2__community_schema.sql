create schema if not exists community;

do
$$
    begin
        if to_regclass('community.discussion_message') is null and
           to_regclass('public.discussion_message') is not null then alter table public.discussion_message
            set schema community;
        end if;
    end
$$;

do
$$
    begin
        if to_regclass('community.accreditation_request') is null and
           to_regclass('public.accreditation_request') is not null then alter table public.accreditation_request
            set schema community;
        end if;
    end
$$;

do
$$
    begin
        if to_regclass('community.community_accreditation') is null and
           to_regclass('public.community_accreditation') is not null then alter table public.community_accreditation
            set schema community;
        end if;
    end
$$;

create table if not exists community.discussion_message
(
    id                BIGSERIAL primary key,
    target_type       VARCHAR(255)  not null,
    target_id         VARCHAR(255)  not null,
    parent_message_id BIGINT,
    author_id         BIGINT        not null,
    content           VARCHAR(4000) not null,
    contribution_type VARCHAR(255)  not null,
    created_at        TIMESTAMPTZ   not null
);

create table if not exists community.accreditation_request
(
    id                   BIGSERIAL primary key,
    requested_by_user_id BIGINT        not null,
    scope_type           VARCHAR(255)  not null,
    scenario_id          BIGINT,
    motivation           VARCHAR(2000) not null,
    status               VARCHAR(255)  not null,
    reviewed_by_user_id  BIGINT,
    reviewed_at          TIMESTAMPTZ,
    review_note          VARCHAR(1500),
    created_at           TIMESTAMPTZ   not null
);

create table if not exists community.community_accreditation
(
    id                 BIGSERIAL primary key,
    user_id            BIGINT       not null,
    scope_type         VARCHAR(255) not null,
    scenario_id        BIGINT,
    granted_by_user_id BIGINT       not null,
    granted_at         TIMESTAMPTZ  not null,
    note               VARCHAR(1500)
);

create index if not exists idx_discussion_message_target on community.discussion_message (target_type, target_id, created_at);

create index if not exists idx_discussion_message_parent on community.discussion_message (parent_message_id);

create index if not exists idx_accreditation_request_scope on community.accreditation_request (scope_type, scenario_id, created_at);

create unique index if not exists uq_community_accreditation_scope on community.community_accreditation (user_id, scope_type, scenario_id);