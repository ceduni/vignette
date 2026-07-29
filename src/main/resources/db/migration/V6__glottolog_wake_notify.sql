-- V6: wake the Python worker via LISTEN/NOTIFY (no 30s polling for schedule).
-- Channel: glottolog_wake
-- Payloads: manual_request | config_changed

CREATE OR REPLACE FUNCTION glottolog_notify_wake()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF TG_TABLE_NAME = 'glottolog_update_request' THEN
        IF TG_OP = 'INSERT' AND NEW.status = 'PENDING' THEN
            PERFORM pg_notify('glottolog_wake', 'manual_request');
        ELSIF TG_OP = 'UPDATE'
            AND NEW.status = 'PENDING'
            AND OLD.status IS DISTINCT FROM 'PENDING' THEN
            PERFORM pg_notify('glottolog_wake', 'manual_request');
        END IF;
    ELSIF TG_TABLE_NAME = 'glottolog_admin_settings' THEN
        PERFORM pg_notify('glottolog_wake', 'config_changed');
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_glottolog_request_wake ON glottolog_update_request;
CREATE TRIGGER trg_glottolog_request_wake
    AFTER INSERT OR UPDATE
    ON glottolog_update_request
    FOR EACH ROW
EXECUTE FUNCTION glottolog_notify_wake();

DROP TRIGGER IF EXISTS trg_glottolog_settings_wake ON glottolog_admin_settings;
CREATE TRIGGER trg_glottolog_settings_wake
    AFTER UPDATE
    ON glottolog_admin_settings
    FOR EACH ROW
EXECUTE FUNCTION glottolog_notify_wake();
