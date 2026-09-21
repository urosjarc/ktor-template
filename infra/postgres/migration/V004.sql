CREATE OR REPLACE FUNCTION audit_new_table()
    RETURNS event_trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    obj record;
BEGIN
    FOR obj IN
        SELECT *
        FROM pg_event_trigger_ddl_commands()
        WHERE command_tag = 'CREATE TABLE'
          AND schema_name = 'public'
        LOOP

            -- Never audit the audit table itself.
            IF obj.object_identity = 'public.logs_audit' THEN
                CONTINUE;
            END IF;

            EXECUTE format(
                    'CREATE TRIGGER %I
                     AFTER INSERT OR UPDATE OR DELETE
                     ON %s
                     FOR EACH ROW
                     EXECUTE FUNCTION audit_row_change()',
                    'audit_' || obj.objid,
                    obj.object_identity
                    );

        END LOOP;
END;
$$;

CREATE EVENT TRIGGER audit_tables
    ON ddl_command_end
    WHEN TAG IN ('CREATE TABLE')
EXECUTE FUNCTION audit_new_table();
