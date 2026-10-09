package org.dromara.agentoa;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FlowableTransactionTest {
    @Test void businessAndEngineCommitAndRollbackTogether() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1", "sa", "");
        var manager = new DataSourceTransactionManager(ds);
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE business_probe(id VARCHAR(64) PRIMARY KEY)");
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(ds); config.setTransactionManager(manager);
        config.setDatabaseSchemaUpdate("true"); config.setAsyncExecutorActivate(false);
        var engine = config.buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment().addClasspathResource("foundation/foundation-probe.bpmn20.xml").deploy();
            var tx = new TransactionTemplate(manager);
            assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
                jdbc.update("INSERT INTO business_probe VALUES('rollback')");
                engine.getRuntimeService().startProcessInstanceByKey("foundationProbe","rollback",Map.of("assignee","1"));
                throw new IllegalStateException("Expected rollback");
            }));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM business_probe",Integer.class));
            assertEquals(0,engine.getRuntimeService().createProcessInstanceQuery().count());
            tx.executeWithoutResult(s -> {
                jdbc.update("INSERT INTO business_probe VALUES('commit')");
                engine.getRuntimeService().startProcessInstanceByKey("foundationProbe","commit",Map.of("assignee","1"));
            });
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM business_probe",Integer.class));
            assertEquals(1,engine.getTaskService().createTaskQuery().taskAssignee("1").count());
            String taskId=engine.getTaskService().createTaskQuery().singleResult().getId();
            engine.getTaskService().complete(taskId);
            assertEquals(0,engine.getRuntimeService().createProcessInstanceQuery().count());
        } finally { engine.close(); }
    }
}
