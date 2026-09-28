package com.tardistock.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Component
public class EmailUniquenessAuditRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(EmailUniquenessAuditRunner.class);

    private final DataSource dataSource;

    public EmailUniquenessAuditRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            long totalMembers = scalar(connection, "SELECT COUNT(*) FROM member");
            long distinctEmails = scalar(
                    connection,
                    "SELECT COUNT(DISTINCT LOWER(email)) FROM member"
            );
            long duplicateGroups = scalar(
                    connection,
                    """
                    SELECT COUNT(*)
                    FROM (
                        SELECT LOWER(email)
                        FROM member
                        GROUP BY LOWER(email)
                        HAVING COUNT(*) > 1
                    ) duplicate_email_groups
                    """
            );

            log.info(
                    "EMAIL_UNIQUENESS_AUDIT totalMembers={} distinctEmails={} duplicateGroups={}",
                    totalMembers,
                    distinctEmails,
                    duplicateGroups
            );
        }
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new IllegalStateException("Aggregate audit returned no row");
            }
            return resultSet.getLong(1);
        }
    }
}
