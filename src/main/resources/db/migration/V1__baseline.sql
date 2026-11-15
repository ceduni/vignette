-- Baseline of the whole schema (replaces the former V1..V16 migrations, Hibernate
-- ddl-auto=update and the *SchemaMaintenance startup classes).
--
-- Generated with `pg_dump --schema-only --no-owner --no-privileges` from a database
-- built from the JPA entities, so it is exactly what Hibernate validates against.
--
-- Never edit this file: every schema change goes in a new V<n>__<description>.sql.
-- Databases that predate Flyway are marked as "already at V1" (baseline) instead of
-- running this script; see "Database schema" in the README.

CREATE SCHEMA IF NOT EXISTS community;

CREATE TABLE community.accreditation_request (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    motivation character varying(2000) NOT NULL,
    permission_type character varying(255) NOT NULL,
    requested_by_user_id bigint NOT NULL,
    review_note character varying(1500),
    reviewed_at timestamp(6) with time zone,
    reviewed_by_user_id bigint,
    scope_type character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    target_id character varying(255),
    CONSTRAINT accreditation_request_permission_type_check CHECK (((permission_type)::text = ANY ((ARRAY['COMMUNITY_REVIEW'::character varying, 'LANGUAGE_EDIT'::character varying, 'SCENARIO_EDIT'::character varying, 'SCENARIO_MODERATE'::character varying])::text[]))),
    CONSTRAINT accreditation_request_scope_type_check CHECK (((scope_type)::text = ANY ((ARRAY['GLOBAL'::character varying, 'SCENARIO'::character varying, 'LANGUAGE'::character varying, 'LANGUAGE_FAMILY'::character varying])::text[]))),
    CONSTRAINT accreditation_request_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);

CREATE TABLE community.community_accreditation (
    id bigint NOT NULL,
    granted_at timestamp(6) with time zone NOT NULL,
    granted_by_user_id bigint NOT NULL,
    note character varying(1500),
    permission_type character varying(255) NOT NULL,
    scope_type character varying(255) NOT NULL,
    target_id character varying(255),
    user_id bigint NOT NULL,
    CONSTRAINT community_accreditation_permission_type_check CHECK (((permission_type)::text = ANY ((ARRAY['COMMUNITY_REVIEW'::character varying, 'LANGUAGE_EDIT'::character varying, 'SCENARIO_EDIT'::character varying, 'SCENARIO_MODERATE'::character varying])::text[]))),
    CONSTRAINT community_accreditation_scope_type_check CHECK (((scope_type)::text = ANY ((ARRAY['GLOBAL'::character varying, 'SCENARIO'::character varying, 'LANGUAGE'::character varying, 'LANGUAGE_FAMILY'::character varying])::text[])))
);

CREATE TABLE community.discussion_message (
    id bigint NOT NULL,
    author_id bigint NOT NULL,
    content character varying(4000) NOT NULL,
    contribution_type character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    parent_message_id bigint,
    target_id character varying(255) NOT NULL,
    target_type character varying(255) NOT NULL,
    CONSTRAINT discussion_message_contribution_type_check CHECK (((contribution_type)::text = ANY ((ARRAY['GENERAL'::character varying, 'TRANSCRIPTION'::character varying, 'TRANSLATION'::character varying, 'GLOSS'::character varying, 'INTERPRETATION'::character varying])::text[]))),
    CONSTRAINT discussion_message_target_type_check CHECK (((target_type)::text = ANY ((ARRAY['LANGUAGE'::character varying, 'AUDIO'::character varying, 'SCENARIO'::character varying])::text[])))
);

CREATE SEQUENCE public.accreditation_request_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.audio (
    id bigint NOT NULL,
    audio_sha256 character varying(64) NOT NULL,
    author_id bigint NOT NULL,
    background_loop boolean,
    background_volume integer,
    free_translation text,
    gloss text,
    idx integer NOT NULL,
    language_id character varying(255) NOT NULL,
    marker_label character varying(120),
    marker_x double precision,
    marker_y double precision,
    mime character varying(64) NOT NULL,
    original_filename character varying(255),
    scenario_id bigint NOT NULL,
    scope character varying(32) DEFAULT 'SCENE'::character varying NOT NULL,
    size_bytes bigint NOT NULL,
    source_label character varying(180),
    source_url character varying(512),
    storage_path character varying(512) NOT NULL,
    thumbnail_id bigint,
    title character varying(255) NOT NULL,
    transcription text,
    CONSTRAINT audio_scope_check CHECK (((scope)::text = ANY ((ARRAY['SCENE'::character varying, 'BACKGROUND'::character varying])::text[])))
);

CREATE SEQUENCE public.audio_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.author (
    id bigint NOT NULL,
    name character varying(255),
    surname character varying(255)
);

CREATE SEQUENCE public.author_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.community_accreditation_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.discussion_message_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.glottolog_admin_settings (
    id bigint NOT NULL,
    auto_update_enabled boolean NOT NULL,
    frequency_days integer,
    notification_email character varying(255),
    update_frequency character varying(32) NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by_username character varying(255),
    CONSTRAINT glottolog_admin_settings_update_frequency_check CHECK (((update_frequency)::text = ANY ((ARRAY['MANUAL'::character varying, 'TWO_WEEKS'::character varying, 'ONE_MONTH'::character varying, 'THREE_MONTHS'::character varying, 'SIX_MONTHS'::character varying])::text[])))
);

CREATE TABLE public.glottolog_notification (
    id bigint NOT NULL,
    body character varying(8000) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    delivery_detail character varying(1000),
    delivery_status character varying(32) NOT NULL,
    history_id bigint,
    pipeline_status character varying(64),
    subject character varying(500) NOT NULL,
    to_email character varying(255) NOT NULL,
    trigger_type character varying(32)
);

ALTER TABLE public.glottolog_notification ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.glottolog_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.glottolog_pipeline_state (
    id bigint NOT NULL,
    active_history_id bigint,
    config_auto_update_enabled boolean,
    config_frequency_days integer,
    config_observed_at timestamp(6) with time zone,
    current_status character varying(64) NOT NULL,
    last_check_at timestamp(6) with time zone,
    last_detected_checksum character varying(128),
    last_downloaded_checksum character varying(128),
    last_error character varying(4000),
    last_imported_checksum character varying(128),
    last_success_at timestamp(6) with time zone,
    last_validated_checksum character varying(128),
    lock_expires_at timestamp(6) with time zone,
    lock_heartbeat_at timestamp(6) with time zone,
    lock_owner character varying(255),
    next_retry_at timestamp(6) with time zone,
    next_run_at timestamp(6) with time zone,
    retry_count integer NOT NULL,
    status_message character varying(2000),
    updated_at timestamp(6) with time zone
);

CREATE TABLE public.glottolog_update_history (
    id bigint NOT NULL,
    database_count bigint,
    download_duration_ms bigint,
    error_message character varying(4000),
    finished_at timestamp(6) with time zone,
    glottolog_version character varying(64),
    imported_checksum character varying(128),
    inserted_count integer,
    pipeline_status character varying(64),
    remote_checksum character varying(128),
    retry_count integer,
    selected_rows integer,
    source_rows integer,
    started_at timestamp(6) with time zone NOT NULL,
    status character varying(32) NOT NULL,
    sync_duration_ms bigint,
    trigger_type character varying(32),
    triggered_by_username character varying(255) NOT NULL,
    unchanged_count integer,
    updated_count integer,
    worker_id character varying(255),
    CONSTRAINT glottolog_update_history_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying])::text[])))
);

ALTER TABLE public.glottolog_update_history ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.glottolog_update_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.glottolog_update_request (
    id bigint NOT NULL,
    claimed_at timestamp(6) with time zone,
    claimed_by character varying(255),
    error_message character varying(4000),
    finished_at timestamp(6) with time zone,
    heartbeat_at timestamp(6) with time zone,
    history_id bigint,
    requested_at timestamp(6) with time zone NOT NULL,
    requested_by_username character varying(255) NOT NULL,
    started_at timestamp(6) with time zone,
    status character varying(32) NOT NULL,
    CONSTRAINT glottolog_update_request_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CLAIMED'::character varying, 'RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);

ALTER TABLE public.glottolog_update_request ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.glottolog_update_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.language (
    id character varying(255) NOT NULL,
    bookkeeping boolean NOT NULL,
    child_dialect_count integer NOT NULL,
    child_family_count integer NOT NULL,
    child_language_count integer NOT NULL,
    country_ids character varying(255),
    description character varying(10000),
    family_id character varying(255),
    iso639_p3code character varying(255),
    last_seen_in_glottolog_at timestamp(6) with time zone,
    latitude real,
    level character varying(255) NOT NULL,
    longitude real,
    markup_description character varying(10000),
    name character varying(255) NOT NULL,
    parent_id character varying(255),
    present_in_latest_glottolog boolean
);

CREATE TABLE public.language_follow (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    language_id character varying(255) NOT NULL,
    user_id bigint NOT NULL
);

ALTER TABLE public.language_follow ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.language_follow_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.notification (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    message character varying(500) NOT NULL,
    read boolean NOT NULL,
    reference_id bigint,
    target_url character varying(500),
    type character varying(64) NOT NULL,
    user_id bigint NOT NULL
);

ALTER TABLE public.notification ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.role (
    name character varying(255) NOT NULL
);

CREATE TABLE public.scenario (
    id bigint NOT NULL,
    active_background_audio_id bigint,
    author_id bigint,
    created_at timestamp(6) with time zone,
    description character varying(500),
    language_id character varying(255),
    latitude double precision,
    location_name character varying(200),
    longitude double precision,
    parent_scenario_id bigint,
    published_at timestamp(6) with time zone,
    review_comment character varying(255),
    review_status character varying(255) NOT NULL,
    reviewed_at timestamp(6) with time zone,
    reviewed_by_id bigint,
    storyboard_columns integer NOT NULL,
    storyboard_layout_mode character varying(255) NOT NULL,
    storyboard_preset character varying(64) NOT NULL,
    title character varying(255) NOT NULL,
    visibility_status character varying(255) NOT NULL,
    CONSTRAINT scenario_review_status_check CHECK (((review_status)::text = ANY ((ARRAY['NONE'::character varying, 'PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT scenario_storyboard_layout_mode_check CHECK (((storyboard_layout_mode)::text = ANY ((ARRAY['PRESET'::character varying, 'CUSTOM'::character varying])::text[]))),
    CONSTRAINT scenario_visibility_status_check CHECK (((visibility_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying])::text[])))
);

CREATE TABLE public.scenario_bookmark (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    scenario_id bigint NOT NULL,
    user_id bigint NOT NULL
);

ALTER TABLE public.scenario_bookmark ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.scenario_bookmark_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.scenario_collaborator (
    id bigint NOT NULL,
    expires_at timestamp(6) with time zone,
    invited_at timestamp(6) with time zone NOT NULL,
    invited_by_id bigint,
    responded_at timestamp(6) with time zone,
    role character varying(255) NOT NULL,
    scenario_id bigint NOT NULL,
    status character varying(255) NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT scenario_collaborator_role_check CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'EDITOR'::character varying, 'VIEWER'::character varying])::text[]))),
    CONSTRAINT scenario_collaborator_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying])::text[])))
);

CREATE SEQUENCE public.scenario_collaborator_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.scenario_history_entry (
    id bigint NOT NULL,
    action character varying(255) NOT NULL,
    actor_user_id bigint NOT NULL,
    actor_username character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    scenario_id bigint NOT NULL,
    summary character varying(255) NOT NULL,
    CONSTRAINT scenario_history_entry_action_check CHECK (((action)::text = ANY ((ARRAY['SCENARIO_CREATED'::character varying, 'METADATA_UPDATED'::character varying, 'STORYBOARD_UPDATED'::character varying, 'THUMBNAIL_ADDED'::character varying, 'THUMBNAIL_UPDATED'::character varying, 'THUMBNAIL_DELETED'::character varying, 'AUDIO_ADDED'::character varying, 'AUDIO_UPDATED'::character varying, 'AUDIO_DELETED'::character varying, 'PUBLISHED'::character varying])::text[])))
);

CREATE SEQUENCE public.scenario_history_entry_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.scenario_invite_link (
    id bigint NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by_id bigint NOT NULL,
    expires_at timestamp(6) with time zone,
    max_uses integer,
    role character varying(255) NOT NULL,
    scenario_id bigint NOT NULL,
    token character varying(64) NOT NULL,
    use_count integer NOT NULL,
    CONSTRAINT scenario_invite_link_role_check CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'EDITOR'::character varying, 'VIEWER'::character varying])::text[])))
);

CREATE SEQUENCE public.scenario_invite_link_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.scenario_like (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    scenario_id bigint NOT NULL,
    user_id bigint NOT NULL
);

ALTER TABLE public.scenario_like ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.scenario_like_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE SEQUENCE public.scenario_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.scenario_tag (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(64) NOT NULL,
    normalized_name character varying(64) NOT NULL
);

CREATE TABLE public.scenario_tag_link (
    scenario_id bigint NOT NULL,
    tag_id bigint NOT NULL
);

CREATE SEQUENCE public.scenario_tag_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.thumbnail (
    id bigint NOT NULL,
    author_id bigint NOT NULL,
    content_type character varying(255),
    grid_column integer,
    grid_column_span integer,
    grid_row integer,
    grid_row_span integer,
    idx integer NOT NULL,
    image_height integer,
    image_sha256 character varying(64) NOT NULL,
    image_width integer,
    original_filename character varying(255),
    scenario_id bigint NOT NULL,
    size_bytes bigint NOT NULL,
    storage_path character varying(512) NOT NULL,
    title character varying(255) NOT NULL
);

CREATE SEQUENCE public.thumbnail_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.user_ (
    bio character varying(1500),
    display_name character varying(255),
    email character varying(255) NOT NULL,
    institution character varying(255),
    password_hash character varying(255) NOT NULL,
    profile_public boolean NOT NULL,
    research_interests character varying(1000),
    username character varying(255) NOT NULL,
    id bigint NOT NULL
);

CREATE TABLE public.user_academy_affiliations (
    user_id bigint NOT NULL,
    academy_name character varying(255)
);

CREATE TABLE public.user_roles (
    user_id bigint NOT NULL,
    role_name character varying(255) NOT NULL
);

CREATE TABLE public.vignette_maker_scene (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(200) NOT NULL,
    scene_json text NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id bigint NOT NULL
);

ALTER TABLE public.vignette_maker_scene ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.vignette_maker_scene_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

ALTER TABLE ONLY community.accreditation_request
    ADD CONSTRAINT accreditation_request_pkey PRIMARY KEY (id);

ALTER TABLE ONLY community.community_accreditation
    ADD CONSTRAINT community_accreditation_pkey PRIMARY KEY (id);

ALTER TABLE ONLY community.discussion_message
    ADD CONSTRAINT discussion_message_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.audio
    ADD CONSTRAINT audio_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.author
    ADD CONSTRAINT author_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.glottolog_admin_settings
    ADD CONSTRAINT glottolog_admin_settings_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.glottolog_notification
    ADD CONSTRAINT glottolog_notification_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.glottolog_pipeline_state
    ADD CONSTRAINT glottolog_pipeline_state_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.glottolog_update_history
    ADD CONSTRAINT glottolog_update_history_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.glottolog_update_request
    ADD CONSTRAINT glottolog_update_request_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.language_follow
    ADD CONSTRAINT language_follow_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.language
    ADD CONSTRAINT language_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (name);

ALTER TABLE ONLY public.scenario_bookmark
    ADD CONSTRAINT scenario_bookmark_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scenario_collaborator
    ADD CONSTRAINT scenario_collaborator_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scenario_history_entry
    ADD CONSTRAINT scenario_history_entry_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scenario_invite_link
    ADD CONSTRAINT scenario_invite_link_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scenario_like
    ADD CONSTRAINT scenario_like_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scenario
    ADD CONSTRAINT scenario_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scenario_tag_link
    ADD CONSTRAINT scenario_tag_link_pkey PRIMARY KEY (scenario_id, tag_id);

ALTER TABLE ONLY public.scenario_tag
    ADD CONSTRAINT scenario_tag_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.thumbnail
    ADD CONSTRAINT thumbnail_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.scenario_invite_link
    ADD CONSTRAINT uk9w2fc5wk6n51214xkmt35lkpl UNIQUE (token);

ALTER TABLE ONLY public.scenario
    ADD CONSTRAINT uk_scenario_author_language_title UNIQUE (author_id, language_id, title);

ALTER TABLE ONLY public.scenario_tag
    ADD CONSTRAINT uk_scenario_tag_normalized_name UNIQUE (normalized_name);

ALTER TABLE ONLY public.language
    ADD CONSTRAINT ukg8hr207ijpxlwu10pewyo65gv UNIQUE (name);

ALTER TABLE ONLY public.user_
    ADD CONSTRAINT ukha67cvlhy4nk1prswl5gj1y0y UNIQUE (email);

ALTER TABLE ONLY public.language_follow
    ADD CONSTRAINT ukhfak805amgdbdy8ilpxo748gl UNIQUE (language_id, user_id);

ALTER TABLE ONLY public.scenario_collaborator
    ADD CONSTRAINT uki9d6bofmy9ft5b5i44eymrelj UNIQUE (scenario_id, user_id);

ALTER TABLE ONLY public.scenario_like
    ADD CONSTRAINT ukni3e5ohmm71cbpa1qut81h6sy UNIQUE (scenario_id, user_id);

ALTER TABLE ONLY public.language
    ADD CONSTRAINT uko9pc9ih8nlflyveaiaw5njgk9 UNIQUE (iso639_p3code);

ALTER TABLE ONLY public.scenario_bookmark
    ADD CONSTRAINT ukojpoxjciica9ej7g3bdkhjvva UNIQUE (scenario_id, user_id);

ALTER TABLE ONLY public.user_
    ADD CONSTRAINT ukwqsqlvajcne4rlyosglqglhk UNIQUE (username);

ALTER TABLE ONLY public.user_
    ADD CONSTRAINT user__pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_name);

ALTER TABLE ONLY public.vignette_maker_scene
    ADD CONSTRAINT vignette_maker_scene_pkey PRIMARY KEY (id);

CREATE INDEX idx_audio_scenario_scope ON public.audio USING btree (scenario_id, scope, idx);

CREATE INDEX idx_notification_created_at ON public.notification USING btree (created_at);

CREATE INDEX idx_notification_user_id ON public.notification USING btree (user_id);

ALTER TABLE ONLY public.vignette_maker_scene
    ADD CONSTRAINT fk25w5c2v3m5hf7d7s54y9gbcu4 FOREIGN KEY (user_id) REFERENCES public.user_(id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk53v9b6vwc56nm9llvcdhs8y8f FOREIGN KEY (role_name) REFERENCES public.role(name);

ALTER TABLE ONLY public.scenario_like
    ADD CONSTRAINT fk9qqdfj3tjkqnso1v1odvu6dha FOREIGN KEY (user_id) REFERENCES public.user_(id);

ALTER TABLE ONLY public.scenario_bookmark
    ADD CONSTRAINT fkcjqo87b76o8x81f1noaxheg4a FOREIGN KEY (scenario_id) REFERENCES public.scenario(id);

ALTER TABLE ONLY public.language_follow
    ADD CONSTRAINT fke8g1yfs9f51l3qg336ap5jldb FOREIGN KEY (user_id) REFERENCES public.user_(id);

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fkg9wcclio3v5xftqnc4q7lr7hd FOREIGN KEY (user_id) REFERENCES public.user_(id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fki11ca3b26vh3bcucnq39inil3 FOREIGN KEY (user_id) REFERENCES public.user_(id);

ALTER TABLE ONLY public.user_
    ADD CONSTRAINT fkjheeo8ubu31dxj65byj8362l9 FOREIGN KEY (id) REFERENCES public.author(id);

ALTER TABLE ONLY public.scenario_like
    ADD CONSTRAINT fkjk9c43a12w1vmr0m8rqsd3m6n FOREIGN KEY (scenario_id) REFERENCES public.scenario(id);

ALTER TABLE ONLY public.scenario_tag_link
    ADD CONSTRAINT fklfkmt74jc86h8q5dmmsx0876h FOREIGN KEY (scenario_id) REFERENCES public.scenario(id);

ALTER TABLE ONLY public.scenario_bookmark
    ADD CONSTRAINT fknvlrhyxsqdhb6uqu9pt6338q6 FOREIGN KEY (user_id) REFERENCES public.user_(id);

ALTER TABLE ONLY public.language_follow
    ADD CONSTRAINT fkqlby8erscrxp89o08u24ijgt3 FOREIGN KEY (language_id) REFERENCES public.language(id);

ALTER TABLE ONLY public.scenario_tag_link
    ADD CONSTRAINT fkqwycp1f8pkhugyh8awu1qpaoc FOREIGN KEY (tag_id) REFERENCES public.scenario_tag(id);

ALTER TABLE ONLY public.user_academy_affiliations
    ADD CONSTRAINT fks3j5cqoxmtdgw9ggk1stjjcg3 FOREIGN KEY (user_id) REFERENCES public.user_(id);

