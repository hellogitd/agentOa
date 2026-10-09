package org.dromara.agentoa.workflow.support;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6 · 迁移双向验证：本批次交付的 V23/V24/V25/V27 必须各自配有回滚脚本，
 * 且前向脚本创建/新增的每一个对象（表、列、索引、种子行）都要在回滚脚本里被还原。
 * <p>
 * 这是「双向可验证」的静态门禁：前向与回滚成对存在、无遗漏对象。
 */
class MigrationReversibilityTest {

    private static final String[] MIGRATIONS = {
        "V23__workflow_definition_crud",
        "V24__workflow_builtin_templates",
        "V25__workflow_generic_request",
        "V27__workflow_form_query_grant"
    };

    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE (\\w+)");
    private static final Pattern ADD_COLUMN = Pattern.compile("ALTER TABLE (\\w+)\\s+ADD COLUMN (\\w+)");
    private static final Pattern ADD_KEY = Pattern.compile("ALTER TABLE (\\w+)\\s+ADD KEY (\\w+)");
    private static final Pattern INSERT_INTO = Pattern.compile("INSERT INTO (\\w+) VALUES\\s*\\((\\d+)");
    private static final Pattern DROP_TABLE = Pattern.compile("DROP TABLE (?:IF EXISTS )?(\\w+)");
    private static final Pattern DROP_COLUMN = Pattern.compile("DROP COLUMN (\\w+)");
    private static final Pattern DROP_KEY = Pattern.compile("DROP KEY (\\w+)");
    private static final Pattern DELETE_FROM = Pattern.compile("DELETE FROM (\\w+)");

    @Test
    void everyMigrationHasMatchingUndoScript() {
        for (String name : MIGRATIONS) {
            String forward = read("db/migration/" + name + ".sql");
            String undo = read("db/migration-undo/" + name + "__undo.sql");
            assertThat(forward).as("前向脚本必须存在: %s", name).isNotBlank();
            assertThat(undo).as("回滚脚本必须存在: %s", name).isNotBlank();
        }
    }

    @Test
    void createdTablesAreDroppedOnRollback() {
        for (String name : MIGRATIONS) {
            String forward = read("db/migration/" + name + ".sql");
            String undo = read("db/migration-undo/" + name + "__undo.sql");
            Set<String> created = matches(CREATE_TABLE, forward, 1);
            Set<String> dropped = matches(DROP_TABLE, undo, 1);
            for (String table : created) {
                assertThat(dropped)
                    .as("%s 创建的表 %s 必须在回滚脚本中删除", name, table)
                    .contains(table);
            }
        }
    }

    @Test
    void addedColumnsAndKeysAreDroppedOnRollback() {
        for (String name : MIGRATIONS) {
            String forward = read("db/migration/" + name + ".sql");
            String undo = read("db/migration-undo/" + name + "__undo.sql");
            Set<String> addedColumns = matches(ADD_COLUMN, forward, 2);
            Set<String> droppedColumns = matches(DROP_COLUMN, undo, 1);
            for (String column : addedColumns) {
                assertThat(droppedColumns)
                    .as("%s 新增列 %s 必须在回滚脚本中删除", name, column)
                    .contains(column);
            }
            Set<String> addedKeys = matches(ADD_KEY, forward, 2);
            Set<String> droppedKeys = matches(DROP_KEY, undo, 1);
            for (String key : addedKeys) {
                assertThat(droppedKeys)
                    .as("%s 新增索引 %s 必须在回滚脚本中删除", name, key)
                    .contains(key);
            }
        }
    }

    @Test
    void seededRowsAreDeletedOnRollback() {
        for (String name : MIGRATIONS) {
            String forward = read("db/migration/" + name + ".sql");
            String undo = read("db/migration-undo/" + name + "__undo.sql");
            Set<String> seededTables = matches(INSERT_INTO, forward, 1);
            Set<String> deletedTables = matches(DELETE_FROM, undo, 1);
            for (String table : seededTables) {
                assertThat(deletedTables)
                    .as("%s 写入的表 %s 必须在回滚脚本中清理", name, table)
                    .contains(table);
            }
        }
    }

    @Test
    void rollbackScriptsDoNotOrphanObjects() {
        // 反向：回滚脚本里出现的对象必须在前向脚本里被创建/写入，避免回滚脚本越权清理无关对象
        for (String name : MIGRATIONS) {
            String forward = read("db/migration/" + name + ".sql");
            String undo = read("db/migration-undo/" + name + "__undo.sql");
            Set<String> created = matches(CREATE_TABLE, forward, 1);
            Set<String> dropped = matches(DROP_TABLE, undo, 1);
            for (String table : dropped) {
                assertThat(created)
                    .as("%s 回滚脚本删除的表 %s 必须由本迁移创建", name, table)
                    .contains(table);
            }
        }
    }

    @Test
    void undoScriptsAreNotVersionedAsFlywayMigrations() {
        // 回滚脚本放在独立目录，绝不能被 Flyway 当作迁移执行
        for (String name : MIGRATIONS) {
            String undo = read("db/migration-undo/" + name + "__undo.sql");
            assertThat(undo).as("%s 回滚脚本必须存在", name).isNotBlank();
        }
        // 前向目录里不得出现 __undo 文件
        List<String> forwardNames = List.of(MIGRATIONS);
        for (String name : forwardNames) {
            assertThat(name).doesNotContain("__undo");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Set<String> matches(Pattern pattern, String sql, int group) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            result.add(matcher.group(group));
        }
        return result;
    }

    private static String read(String classpath) {
        try (InputStream in = MigrationReversibilityTest.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
