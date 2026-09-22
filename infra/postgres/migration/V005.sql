CREATE OR REPLACE FUNCTION public.set_inserted_at()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    NEW.inserted_at = now();
    RETURN NEW;
END;
$$;


CREATE OR REPLACE FUNCTION public.set_updated_at()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at = now();
    END IF;

    RETURN NEW;
END;
$$;


CREATE OR REPLACE FUNCTION public.add_default_timestamptz_to_table()
    RETURNS event_trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    obj RECORD;
BEGIN
    FOR obj IN
        SELECT *
        FROM pg_event_trigger_ddl_commands()
        WHERE command_tag = 'CREATE TABLE'
          AND schema_name = 'public'
        LOOP
            EXECUTE format(
                    'ALTER TABLE %s
                     ADD COLUMN IF NOT EXISTS inserted_at TIMESTAMPTZ NOT NULL,
                     ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL',
                    obj.object_identity
                    );

            EXECUTE format(
                    'CREATE TRIGGER %I
                     BEFORE INSERT ON %s
                     FOR EACH ROW
                     EXECUTE FUNCTION public.set_inserted_at()',
                    'trg_' || obj.objid || '_inserted_at',
                    obj.object_identity
                    );

            EXECUTE format(
                    'CREATE TRIGGER %I
                     BEFORE UPDATE ON %s
                     FOR EACH ROW
                     EXECUTE FUNCTION public.set_updated_at()',
                    'trg_' || obj.objid || '_updated_at',
                    obj.object_identity
                    );
        END LOOP;
END;
$$;


CREATE EVENT TRIGGER trg_public_table_timestamptz
    ON ddl_command_end
    WHEN TAG IN ('CREATE TABLE')
EXECUTE FUNCTION public.add_default_timestamptz_to_table();

CREATE OR REPLACE FUNCTION public.add_timestamptz_triggers(
    p_schema_name TEXT,
    p_table_name TEXT
)
    RETURNS VOID
    LANGUAGE plpgsql
AS
$$
BEGIN
    EXECUTE format(
            'CREATE TRIGGER %I
             BEFORE INSERT ON %I.%I
             FOR EACH ROW
             EXECUTE FUNCTION public.set_inserted_at()',
            'trg_' || p_table_name || '_inserted_at',
            p_schema_name,
            p_table_name
            );

    EXECUTE format(
            'CREATE TRIGGER %I
             BEFORE UPDATE ON %I.%I
             FOR EACH ROW
             EXECUTE FUNCTION public.set_updated_at()',
            'trg_' || p_table_name || '_updated_at',
            p_schema_name,
            p_table_name
            );
END;
$$;
