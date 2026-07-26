package com.cardio.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AIServiceTest {

    private AIService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AIService("http://localhost:8000/predict", "https://generativelanguage.googleapis.com");
    }

    @Test
    @DisplayName("Test AI symptom advice fallback with acute symptoms")
    void testGetSymptomAdviceWithAcuteSymptoms() {
        String symptoms = "Đau ngực dữ dội, Khó thở";
        Map<String, String> result = aiService.getSymptomAdvice(symptoms, 9, "Kéo dài trên 24 giờ", "Cần hỗ trợ khẩn cấp");

        assertNotNull(result);
        assertTrue(result.containsKey("aiRiskAssessment"));
        assertTrue(result.containsKey("aiAdvice"));
    }

    @Test
    @DisplayName("Test AI symptom advice fallback with mild symptoms and null fields")
    void testGetSymptomAdviceWithMildSymptomsAndNulls() {
        String symptoms = "Hơi mệt mỏi";
        Map<String, String> result = aiService.getSymptomAdvice(symptoms, 2, "", null);

        assertNotNull(result);
        assertNotNull(result.get("aiAdvice"));
    }

    @Test
    @DisplayName("Test risk tier mapping to Vietnamese")
    void testMapRiskTierToVietnamese() {
        assertEquals("RỦI RO CAO", aiService.mapRiskTierToVietnamese("HIGH"));
        assertEquals("CẦN THEO DÕI", aiService.mapRiskTierToVietnamese("MEDIUM"));
        assertEquals("AN TOÀN", aiService.mapRiskTierToVietnamese(null));
    }
}
