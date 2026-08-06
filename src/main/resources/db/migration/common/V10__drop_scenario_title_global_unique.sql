do
$$
    declare
        constraint_name text;
    begin
        if to_regclass('scenario') is not null then
            select con.conname
            into constraint_name
            from pg_constraint con
                     join pg_class rel on rel.oid = con.conrelid
            where rel.relname = 'scenario'
              and con.contype = 'u'
              and (select array_agg(attname::text order by attnum)
                   from pg_attribute
                   where attrelid = rel.oid
                     and array[attnum] <@ con.conkey) = array['title'];

            if constraint_name is not null then
                execute format('alter table scenario drop constraint %I', constraint_name);
            end if;
        end if;
    end
$$;
