-- V7: Admin notification email for Glottolog update activity (Java-owned).
alter table glottolog_admin_settings
    add column notification_email varchar(255);
