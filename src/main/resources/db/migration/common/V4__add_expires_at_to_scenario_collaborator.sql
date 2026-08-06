do
$$
    begin
        if to_regclass('scenario_collaborator') is not null
            and not exists (select 1 from information_schema.columns
                             where table_name = 'scenario_collaborator' and column_name = 'expires_at') then
            alter table scenario_collaborator add column expires_at TIMESTAMP WITH TIME ZONE;
        end if;
    end
$$;