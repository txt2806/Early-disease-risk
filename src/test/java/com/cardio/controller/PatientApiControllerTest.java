package com.cardio.controller;

import com.cardio.repository.SystemLogRepository;
import com.cardio.service.AIService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientApiController.class)
class PatientApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private SystemLogRepository systemLogRepository;

    @MockBean
    private AIService aiService;

    @Test
    @DisplayName("GET /api/patient/history/{patientId} should return medical history list")
    void testGetMedicalHistorySuccess() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), eq(17)))
                .thenReturn(List.of(Collections.singletonMap("RecordID", 87)));

        mockMvc.perform(get("/api/patient/history/17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("GET /api/patient/alerts/{patientId} should return health alerts")
    void testGetAlertsSuccess() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), eq(17)))
                .thenReturn(List.of(Collections.singletonMap("LogID", 101)));

        mockMvc.perform(get("/api/patient/alerts/17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("POST /api/patient/symptoms/update should calculate AI advice and return success")
    void testSubmitSymptomUpdateSuccess() throws Exception {
        when(aiService.getSymptomRiskAssessment(any(), any(), any(), any()))
                .thenReturn("NGUY CƠ CẤP TÍNH CAO");
        when(aiService.getSymptomAdvice(any(), any(), any(), any(), any()))
                .thenReturn("GỌI NGAY CẤP CỨU 115");

        String jsonPayload = """
                {
                    "patientId": 17,
                    "symptoms": ["Đau ngực dữ dội"],
                    "heartRate": 105,
                    "severityScore": 9,
                    "duration": "Kéo dài trên 24 giờ",
                    "notes": "Cần hỗ trợ khẩn cấp"
                }
                """;

        mockMvc.perform(post("/api/patient/symptoms/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.triggeredAlert").value(true));
    }
}
