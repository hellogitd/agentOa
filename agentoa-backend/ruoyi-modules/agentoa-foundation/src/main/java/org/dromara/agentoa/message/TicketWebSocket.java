package org.dromara.agentoa.message;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TicketWebSocket extends TextWebSocketHandler {
    private final Map<String,WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    public TicketWebSocket(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public void afterConnectionEstablished(WebSocketSession session) { session.getAttributes().put("lastSeen",System.currentTimeMillis()); sessions.put(session.getId(),session); }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session.getId()); }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > 256) { session.close(CloseStatus.BAD_DATA); return; }
        try {
            var body=org.dromara.common.json.utils.JsonUtils.parseMap(message.getPayload());
            if(body == null || !"HEARTBEAT".equals(body.get("type"))) { session.close(CloseStatus.BAD_DATA); return; }
        } catch(Exception e) { session.close(CloseStatus.BAD_DATA); return; }
        session.getAttributes().put("lastSeen",System.currentTimeMillis());
        synchronized (session) { session.sendMessage(new TextMessage("{\"type\":\"HEARTBEAT\",\"timestamp\":\""+Instant.now()+"\"}")); }
    }
    public void push(long userId,String payload) {
        sessions.values().stream().filter(s -> Objects.equals(s.getAttributes().get("userId"),userId)).forEach(s -> {
            try { synchronized(s) { if(s.isOpen()) s.sendMessage(new TextMessage(payload)); } } catch(Exception ignored) { sessions.remove(s.getId()); }
        });
    }
    @Scheduled(fixedDelay=30000) public void expireSessions() {
        sessions.values().forEach(s -> {
            try {
                String token = (String)s.getAttributes().get("token");
                boolean active = StpUtil.getLoginIdByToken(token) != null && StpUtil.getStpLogic().getTokenActiveTimeoutByToken(token) != -2;
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0'",Integer.class,s.getAttributes().get("userId"));
                if(!active || count == 0 || System.currentTimeMillis()-(Long)s.getAttributes().get("lastSeen") > 90000) s.close(CloseStatus.POLICY_VIOLATION);
            } catch(Exception ignored) { try {s.close();} catch(Exception ignoredAgain) { } }
        });
    }

    @RestController @RequiredArgsConstructor static class TicketController {
        private final RedissonClient redis;
        @PostMapping("/api/v1/auth/ws-ticket") public R<Map<String,Object>> ticket() {
            String ticket=UUID.randomUUID().toString();
            redis.getBucket("agentoa:ws-ticket:"+ticket,StringCodec.INSTANCE).set(StpUtil.getTokenValue(),Duration.ofSeconds(60));
            return R.ok(Map.of("ticket",ticket,"expiresIn",60));
        }
    }

    @Configuration @EnableWebSocket @RequiredArgsConstructor static class Config implements WebSocketConfigurer {
        private final TicketWebSocket handler;
        private final RedissonClient redis;
        @Value("${agentoa.websocket.allowed-origins}") private String[] origins;
        @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(handler,"/ws").setAllowedOrigins(origins).addInterceptors(new HandshakeInterceptor() {
                public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler ws,Map<String,Object> attrs) {
                    String ticket=UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
                    if(ticket==null || !ticket.matches("[a-f0-9-]{36}")) return false;
                    Object token=redis.getBucket("agentoa:ws-ticket:"+ticket,StringCodec.INSTANCE).getAndDelete();
                    if(token==null || StpUtil.getLoginIdByToken(token.toString())==null) return false;
                    var user=LoginHelper.getLoginUser(token.toString());
                    if(user==null || StpUtil.getStpLogic().getTokenActiveTimeoutByToken(token.toString()) == -2) return false;
                    Integer enabled = handler.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0' AND must_change_password=0", Integer.class, user.getUserId());
                    if(enabled == null || enabled != 1) return false;
                    attrs.put("token",token.toString()); attrs.put("userId",user.getUserId()); return true;
                }
                public void afterHandshake(ServerHttpRequest req,ServerHttpResponse res,WebSocketHandler ws,Exception ex) { }
            });
        }
    }
}
