package org.dromara.agentoa.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit one-shot DDL process. Runtime credentials never need CREATE/ALTER. */
public final class FoundationMigrator {
    private FoundationMigrator() { }
    public static String setting(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }
    public static String secret(String key, String filename) {
        try {
            Path path = Path.of(setting("SECRETS_DIR", "/run/secrets"), filename);
            return Files.exists(path) ? Files.readString(path).strip() : setting(key, "");
        } catch (Exception e) { throw new IllegalStateException("Cannot read secret " + filename, e); }
    }
    public static void migrate() {
        String url = "jdbc:mysql://" + setting("DB_HOST", "localhost") + ":" + setting("DB_PORT", "13306")
            + "/agentoa?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
        var ds = new DriverManagerDataSource(url, setting("MIGRATION_USER", "agentoa_migrate"), secret("MIGRATION_PASSWORD", "migration-password"));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(ds);
        config.setTransactionManager(new DataSourceTransactionManager(ds));
        config.setDatabaseSchemaUpdate("true");
        config.setAsyncExecutorActivate(false);
        var engine = config.buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment().name("AgentOA foundation probe")
                .enableDuplicateFiltering().addClasspathResource("foundation/foundation-probe.bpmn20.xml").deploy();
        } finally { engine.close(); }
        System.out.println("AgentOA schema migrations completed.");
    }
}
