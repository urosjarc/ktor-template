CREATE OR REPLACE FUNCTION audit_row_change()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    old_json     JSONB;
    new_json     JSONB;
    changes_json JSONB;
    primary_key  JSONB;
BEGIN
    old_json := CASE
                    WHEN TG_OP IN ('UPDATE', 'DELETE')
                        THEN to_jsonb(OLD)
                    ELSE NULL
        END;

    new_json := CASE
                    WHEN TG_OP IN ('INSERT', 'UPDATE')
                        THEN to_jsonb(NEW)
                    ELSE NULL
        END;

    /*
     * Build the primary key from PostgreSQL's catalog.
     */
    SELECT jsonb_object_agg(
                   a.attname,
                   CASE TG_OP
                       WHEN 'DELETE' THEN old_json -> a.attname
                       ELSE new_json -> a.attname
                       END
           )
    INTO primary_key
    FROM pg_index i
             JOIN pg_attribute a
                  ON a.attrelid = i.indrelid
                      AND a.attnum = ANY (i.indkey)
    WHERE i.indrelid = TG_RELID
      AND i.indisprimary;

/*
 * Build the changes.
 */
    IF TG_OP = 'INSERT' THEN

        changes_json := jsonb_build_object('new', new_json);

    ELSIF TG_OP = 'DELETE' THEN

        changes_json := jsonb_build_object('old', old_json);

    ELSE

        SELECT COALESCE(
                       jsonb_object_agg(
                               key,
                               jsonb_build_object(
                                       'old', old_json -> key,
                                       'new', new_json -> key
                               )
                       ),
                       '{}'::jsonb
               )
        INTO changes_json
        FROM jsonb_object_keys(new_json) AS key
        WHERE old_json -> key IS DISTINCT FROM new_json -> key;

    END IF;

    /*
     * Write audit event.
     */
    INSERT INTO logs_audit (user_id,
                            request_id,
                            operation,
                            table_name,
                            primary_key,
                            changes)
    VALUES (current_setting('app.user_id', true),
            current_setting('app.request_id', true),
            TG_OP,
            TG_TABLE_NAME,
            primary_key,
            changes_json);

    RETURN COALESCE(NEW, OLD);
END;
$$;
