package com.kheisark.ldrphotobooth.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class PostgresSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public PostgresSchemaMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql")) {
                return;
            }
        }

        // Hibernate updates the enum column length, but it does not refresh an
        // existing PostgreSQL CHECK constraint when a new enum value is added.
        jdbcTemplate.execute("ALTER TABLE booths DROP CONSTRAINT IF EXISTS booths_status_check");
        jdbcTemplate.execute("""
                ALTER TABLE booths ADD CONSTRAINT booths_status_check
                CHECK (status IN ('WAITING_A', 'WAITING_B', 'READY_TO_FINALIZE', 'COMPLETED'))
                """);
    }
}
