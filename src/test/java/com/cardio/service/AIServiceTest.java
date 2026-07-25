package com.cardio.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;

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
        List<String> symptoms = List.of("Đau ngực dữ dội", "Khó thở");
        String advice = aiService.getSymptomAdvice(symptoms, 105, 9, "Kéo dài trên 24 giờ", "Cần cấp cứu khẩn cấp");

        assertNotNull(advice);
        assertTrue(advice.contains("CẤP CỨU 115") || advice.contains("cấp cứu"), 
                "Advice should contain emergency recommendation");
    }

    @Test
    @DisplayName("Test AI symptom advice fallback with mild symptoms and null fields")
    void testGetSymptomAdviceWithMildSymptomsAndNulls() {
        List<String> symptoms = List.of("Hơi mệt mỏi");
        String advice = aiService.getSymptomAdvice(symptoms, 72, 2, "", null);

        assertNotNull(advice);
        assertFalse(advice.isEmpty(), "Advice should not be empty even when inputs contain nulls");
    }

    @Test
    @DisplayName("Test AI risk assessment for acute chest pain")
    void testGetSymptomRiskAssessmentAcute() {
        List<String> symptoms = List.of("Đau ngực dữ dội");
        String risk = aiService.getSymptomRiskAssessment(symptoms, 110, 8, "Kéo dài 3 giờ");

        assertNotNull(risk);
        assertTrue(risk.contains("CẤP TÍNH") || risk.contains("CỰC KỲ CAO"), 
                "Risk assessment should flag acute risk");
    }
}
