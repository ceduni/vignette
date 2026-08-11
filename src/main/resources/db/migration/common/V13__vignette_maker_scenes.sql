CREATE TABLE IF NOT EXISTS vignette_maker_scene (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES user_(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL DEFAULT 'Untitled Scene',
    scene_json TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vignette_maker_scene_user ON vignette_maker_scene(user_id);
