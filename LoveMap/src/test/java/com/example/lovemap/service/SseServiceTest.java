package com.example.lovemap.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * SseService 多连接单元测试
 * <p>
 * 说明：Spring 的 ResponseBodyEmitter.Handler 是包级私有接口，测试侧无法实现，
 * 因此直接校验连接池状态（白盒），覆盖"同一用户多连接不得互相顶掉"这一回归点。
 */
class SseServiceTest {

    @SuppressWarnings("unchecked")
    private static Map<Integer, Set<SseEmitter>> connectionsOf(SseService service) throws Exception {
        Field field = SseService.class.getDeclaredField("emitterMap");
        field.setAccessible(true);
        return (Map<Integer, Set<SseEmitter>>) field.get(service);
    }

    @Test
    void createConnection_keepsAllConnectionsOfSameUser() throws Exception {
        SseService service = new SseService();

        SseEmitter first = service.createConnection(1);
        SseEmitter second = service.createConnection(1);
        service.createConnection(2);

        assertNotSame(first, second);
        assertEquals(2, connectionsOf(service).get(1).size(), "同一用户的两条连接都应保留");
        assertEquals(2, service.getOnlineCount(), "在线用户数应为 2");

        // 无连接的用户推送不得抛异常
        service.sendEvent(1, "chat-unread-count", Map.of("count", 1));
        service.sendEvent(3, "chat-unread-count", Map.of("count", 1));
    }

    @Test
    void closeConnection_clearsAllConnectionsOfUser() throws Exception {
        SseService service = new SseService();
        service.createConnection(1);
        service.createConnection(1);
        service.createConnection(2);

        service.closeConnection(1);

        assertFalse(connectionsOf(service).containsKey(1), "关闭后该用户的连接条目应被清空");
        assertEquals(1, service.getOnlineCount());
    }
}
