DO
$$
    BEGIN
        CREATE EXTENSION IF NOT EXISTS pgaudit;

        EXECUTE 'ALTER DATABASE ' || current_database() ||
                ' SET pgaudit.log = ''write, ddl, role''';

        RAISE NOTICE 'pgaudit enabled: logging write, ddl, role operations';
    EXCEPTION
        WHEN OTHERS THEN
            RAISE WARNING 'pgaudit not available (shared_preload_libraries missing?): %', SQLERRM;
    END;
$$;
