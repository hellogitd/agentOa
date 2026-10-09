package org.dromara.agentoa.notice;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6 · 迁移双向验证（notice 侧）：V26 必须配有回滚脚本，且前向写入的对象都被还原。
 */
class MigrationReversibilityTest {

    private static final String NAME = "V26__notice_schedule_list_perm";

    private static final Pattern INSERT_INTO = Pattern.compile("INSERT INTO (\\w+) VALUES\\s*\\((\\d+)");
    private static final Pattern DELETE_FROM = Pattern.compile("DELETE FROM (\\w+)");

    @Test
    void migrationHasMatchingUndoScript() {
        assertThat(read("db/migration/" + NAME + ".sql")).isNotBlank();
        assertThat(read("db/migration-undo/" + NAME + "__undo.sql")).isNotBlank();
    }

    @Test
    void seededRowsAreDeletedOnRollback() {
        String forward = read("db/migration/" + NAME + ".sql");
        String undo = read("db/migration-undo/" + NAME + "__undo.sql");
        Set<String> seeded = matches(INSERT_INTO, forward, 1);
        Set<String> deleted = matches(DELETE_FROM, undo, 1);
        assertThat(seeded).contains("sys_menu", "sys_role_menu");
        for (String table : seeded) {
            assertThat(deleted).as("回滚脚本必须清理 %s", table).contains(table);
        }
    }

    @Test
    void rollbackDoesNotTouchUnrelatedObjects() {
        String forward = read("db/migration/" + NAME + ".sql");
        String undo = read("db/migration-undo/" + NAME + "__undo.sql");
        Set<String> seeded = matches(INSERT_INTO, forward, 1);
        for (String table : matches(DELETE_FROM, undo, 1)) {
            assertThat(seeded).as("回滚脚本不得清理本迁移未写入的表 %s", table).contains(table);
        }
    }

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
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
