alter table audio add column if not exists transcription text;
alter table audio add column if not exists gloss text;
alter table audio add column if not exists free_translation text;
alter table audio add column if not exists background_volume integer;
alter table audio add column if not exists background_loop boolean;
