package com.example.lovemap.ai.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiErrorMessagesTest {

    @Test
    void shouldNotTreatDatabaseQuotaErrorAsAiQuotaExhausted() {
        RuntimeException error = new RuntimeException("Table 'lovemap.quota_log' doesn't exist");

        assertFalse(AiErrorMessages.isQuotaExhausted(error));
    }

    @Test
    void shouldRecognizeDashScopeQuotaError() {
        RuntimeException error = new RuntimeException(
                "{\"code\":\"AllocationQuota.FreeTierOnly\",\"message\":\"free tier quota exhausted\"}");

        assertTrue(AiErrorMessages.isQuotaExhausted(error));
    }
}
