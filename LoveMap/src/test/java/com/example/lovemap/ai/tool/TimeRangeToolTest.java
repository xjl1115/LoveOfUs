package com.example.lovemap.ai.tool;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TimeRangeTool.getCurrentTime 秒级时间返回单元测试
 */
class TimeRangeToolTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void getCurrentTime_returnsSecondPrecisionInShanghaiZone() {
        Map<String, Object> r = new TimeRangeTool().getCurrentTime();

        assertEquals(ZONE.getId(), r.get("zone"));
        assertEquals(LocalDate.now(ZONE).toString(), r.get("today"));

        String now = (String) r.get("now");
        assertNotNull(now);
        assertTrue(now.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "now 应为 yyyy-MM-dd HH:mm:ss，实际=" + now);
        LocalDateTime parsed = LocalDateTime.parse(now, DATETIME);
        assertTrue(Math.abs(Duration.between(parsed, LocalDateTime.now(ZONE)).getSeconds()) <= 10,
                "now 应接近当前时间，实际=" + now);
    }
}
