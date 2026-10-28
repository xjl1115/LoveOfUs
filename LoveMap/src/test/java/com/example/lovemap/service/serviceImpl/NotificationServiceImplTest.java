package com.example.lovemap.service.serviceImpl;

import com.example.lovemap.common.Result;
import com.example.lovemap.mapper.NotificationMapper;
import com.example.lovemap.service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private static final String UNREAD_KEY = "notification:unread:9";
    private static final String UNREAD_COUNT_KEY = "notification:unread:count:9";

    private final NotificationMapper notificationMapper = mock(NotificationMapper.class);
    private final SseService sseService = mock(SseService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final NotificationServiceImpl service =
            new NotificationServiceImpl(notificationMapper, sseService, redisTemplate, new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void markAsReadShouldCleanStaleUnreadCacheWhenNotificationNoLongerExists() {
        when(valueOperations.get(UNREAD_KEY))
                .thenReturn("[{\"id\":36,\"userId\":9,\"isRead\":0}]");
        when(notificationMapper.markAsRead(36, 9)).thenReturn(0);
        when(notificationMapper.selectByIdAndUserId(36, 9)).thenReturn(null);
        when(notificationMapper.countUnreadByUserId(9)).thenReturn(0L);

        Result<Void> result = service.markAsRead(9, 36);

        assertTrue(result.isSuccess());
        verify(valueOperations).set(UNREAD_KEY, "[]");
        verify(valueOperations, atLeastOnce()).set(UNREAD_COUNT_KEY, "0");
    }
}
