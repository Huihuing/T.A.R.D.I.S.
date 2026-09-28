package com.tardistock.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.schema-inspection",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchemaInspectionRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(SchemaInspectionRunner.class);
    private static final int LOG_CHUNK_SIZE = 6000;

    private final DataSource dataSource;

    public SchemaInspectionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            List<String> tables = loadBaseTables(connection);
            log.info(
                    "SCHEMA_DUMP_BEGIN database={} tableCount={}",
                    connection.getCatalog(),
                    tables.size()
            );

            for (String table : tables) {
                String ddl = loadCreateTable(connection, table)
                        .replaceAll("\\sAUTO_INCREMENT=\\d+", "");
                logDdl(table, ddl);
            }

            log.info(
                    "SCHEMA_DUMP_END database={} tableCount={}",
                    connection.getCatalog(),
                    tables.size()
            );
        }
    }

    private List<String> loadBaseTables(Connection connection) throws Exception {
        String sql = """
                SELECT TABLE_NAME
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;

        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        }
        return tables;
    }

    private String loadCreateTable(Connection connection, String table)
            throws Exception {
        String quotedTable = "`" + table.replace("`", "``") + "`";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SHOW CREATE TABLE " + quotedTable)) {
            if (!resultSet.next()) {
                throw new IllegalStateException(
                        "SHOW CREATE TABLE returned no row for " + table
                );
            }
            return resultSet.getString(2);
        }
    }

    private void logDdl(String table, String ddl) {
        String encoded = Base64.getEncoder().encodeToString(
                ddl.getBytes(StandardCharsets.UTF_8)
        );
        int totalParts = Math.max(
                1,
                (encoded.length() + LOG_CHUNK_SIZE - 1) / LOG_CHUNK_SIZE
        );

        for (int part = 0; part < totalParts; part++) {
            int start = part * LOG_CHUNK_SIZE;
            int end = Math.min(encoded.length(), start + LOG_CHUNK_SIZE);
            log.info(
                    "SCHEMA_DDL table={} part={}/{} base64={}",
                    table,
                    part + 1,
                    totalParts,
                    encoded.substring(start, end)
            );
        }
    }
}
