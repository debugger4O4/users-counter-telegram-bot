package ru.debugger4o4.userscountertelegrambot.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);

    private final DataSource dataSource;

    @Value("${spring.flyway.enabled}")
    private Boolean fwEnabled;

    public FlywayConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean
    public Flyway flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db.migration")
                .load();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dbMigration() {
        if (fwEnabled != null && fwEnabled) {
            try {
                flyway().migrate();
                logger.info("Migration completed successfully");
            } catch (Exception e) {
                logger.error("Migration error", e);
            }
        }
    }
}
