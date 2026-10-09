package org.dromara.agentoa.message;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 站内信 / 消息中心（API 规范 7.2）：分页补拉、未读统计、已读、全部已读与删除。
 * M5 交付补充：类型/未读过滤、未读分桶、read-all、软删除（del_flag）。
 */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/messages")
public class MessageController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(required = false) Boolean unread) {
        if (pageNum < 1 || pageNum > 100000 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        Long user = LoginHelper.getUserId();
        StringBuilder where = new StringBuilder(" WHERE receiver_id=? AND del_flag=0");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(user);
        if (type != null && !type.isBlank()) {
            where.append(" AND msg_type=?");
            args.add(type.trim());
        }
        if (Boolean.TRUE.equals(unread)) {
            where.append(" AND is_read=0");
        }
        args.add(pageSize);
        args.add((pageNum - 1) * pageSize);
        var rows = jdbc.queryForList("SELECT CAST(id AS CHAR) AS id,msg_type AS type,title,content,"
            + "biz_type AS bizType,CAST(biz_id AS CHAR) AS bizId,is_read AS isRead,create_time AS createTime "
            + "FROM nc_message" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM nc_message" + where, Long.class,
            args.subList(0, args.size() - 2).toArray());
        return R.ok(Map.of("records", rows, "total", total, "pageNum", pageNum, "pageSize", pageSize));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Object>> unread() {
        Long user = LoginHelper.getUserId();
        Map<String, Object> counts = new HashMap<>();
        long total = 0;
        for (Map<String, Object> row : jdbc.queryForList(
            "SELECT msg_type,COUNT(*) AS cnt FROM nc_message WHERE receiver_id=? AND is_read=0 AND del_flag=0 GROUP BY msg_type", user)) {
            long cnt = ((Number) row.get("cnt")).longValue();
            total += cnt;
            counts.put(String.valueOf(row.get("msg_type")).toLowerCase(), cnt);
        }
        return R.ok(Map.of(
            "total", total,
            "todo", counts.getOrDefault("todo", 0L),
            "notice", counts.getOrDefault("notice", 0L),
            "mention", counts.getOrDefault("mention", 0L),
            "system", counts.getOrDefault("system", 0L)));
    }

    @PutMapping("/{id}/read")
    public R<Void> read(@PathVariable long id) {
        int count = jdbc.update("UPDATE nc_message SET is_read=1,read_time=COALESCE(read_time,CURRENT_TIMESTAMP) "
            + "WHERE id=? AND receiver_id=? AND del_flag=0", id, LoginHelper.getUserId());
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        return R.ok();
    }

    /** 全部已读（API 规范 7.2） */
    @PutMapping("/read-all")
    public R<Void> readAll() {
        jdbc.update("UPDATE nc_message SET is_read=1,read_time=COALESCE(read_time,CURRENT_TIMESTAMP) "
            + "WHERE receiver_id=? AND is_read=0 AND del_flag=0", LoginHelper.getUserId());
        return R.ok();
    }

    /** 删除本人消息副本（软删除，保留去重键与审计） */
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable long id) {
        int count = jdbc.update("UPDATE nc_message SET del_flag=1 WHERE id=? AND receiver_id=?", id, LoginHelper.getUserId());
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        return R.ok();
    }
}
