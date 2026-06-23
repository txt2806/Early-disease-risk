package com.cardio.service;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import com.cardio.model.*;
import com.cardio.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * [B.7] Unit test cho ConsultationService — tập trung vào các logic đã sửa
 * ở nhóm A (A.1 encode fbs/exang, A.4 lưu SHAP/trend, A.5 ngưỡng cá nhân
 * hoá) và các trường hợp biên: lịch sử rỗng, field null, dữ liệu thiếu.
 *
 * Dùng Mockito thuần (không cần Spring context đầy đủ) để test nhanh,
 * cô lập đúng logic nghiệp vụ, không phụ thuộc DB thật.
 */
class ConsultationServiceTest {

    private ConsultationRepository consultationRepository;
    private AIRiskRepository aiRiskRepository;
    private AIService aiService;
    private HeartClinicalMetricsRepository heartClinicalMetricsRepository;
    private IcdCatalogRepository icdCatalogRepository;
    private RecordIcdRepository recordIcdRepository;
    private PatientAlertThresholdRepository thresholdRepository;
    private ObjectMapper objectMapper;

    private ConsultationService service;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationRepository.class);
        aiRiskRepository = mock(AIRiskRepository.class);
        aiService = mock(AIService.class);
        heartClinicalMetricsRepository = mock(HeartClinicalMetricsRepository.class);
        icdCatalogRepository = mock(IcdCatalogRepository.class);
        recordIcdRepository = mock(RecordIcdRepository.class);
        thresholdRepository = mock(PatientAlertThresholdRepository.class);
        objectMapper = new ObjectMapper(); // dùng thật, không mock — test serialize JSON thật

        service = new ConsultationService(
                consultationRepository, aiRiskRepository, aiService,
                heartClinicalMetricsRepository, icdCatalogRepository,
                recordIcdRepository, thresholdRepository, objectMapper
        );

        // save() của JpaRepository trả về chính entity được truyền vào (mock mặc định)
        when(consultationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiRiskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(heartClinicalMetricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PatientProfile patient(int id) {
        PatientProfile p = new PatientProfile();
        p.setPatientId(id);
        p.setFullName("Bệnh nhân test " + id);
        return p;
    }

    private DoctorProfile doctor(int id) {
        DoctorProfile d = new DoctorProfile();
        d.setDoctorId(id);
        d.setFullName("BS test " + id);
        return d;
    }

    private AIRequest baseAiRequest() {
        AIRequest req = new AIRequest();
        req.setAge(58.0);
        req.setSex("Male");
        req.setCp("typical angina");
        req.setTrestbps(150.0);
        req.setChol(270.0);
        req.setFbs("1.0");
        req.setRestecg("lv hypertrophy");
        req.setThalch(105.0);
        req.setExang("1.0");
        req.setOldpeak(2.5);
        req.setSlope("flat");
        req.setCa(1.0);
        req.setThal("reversable defect");
        return req;
    }

    // ════════════════════════════════════════════════════════════
    // [A.1] Test parseFbsExangFlag qua hành vi thật (HeartClinicalMetrics lưu xuống)
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Encode fbs/exang khi lưu HeartClinicalMetrics (A.1)")
    class FbsExangEncoding {

        @Test
        @DisplayName("fbs/exang = '1.0' (giá trị CHUẨN sau A.1) -> lưu thành true")
        void standardValue_true() {
            AIRequest req = baseAiRequest();
            req.setFbs("1.0");
            req.setExang("1.0");

            service.saveRecordAfterPrediction(
                    patient(1), doctor(1), "note", "plan",
                    BigDecimal.valueOf(45.0), "MEDIUM", "explanation",
                    req, null, null);

            ArgumentCaptor<HeartClinicalMetrics> captor = ArgumentCaptor.forClass(HeartClinicalMetrics.class);
            verify(heartClinicalMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getFastingBloodSugar()).isTrue();
            assertThat(captor.getValue().getExerciseAngina()).isTrue();
        }

        @Test
        @DisplayName("fbs/exang = '0.0' -> lưu thành false")
        void standardValue_false() {
            AIRequest req = baseAiRequest();
            req.setFbs("0.0");
            req.setExang("0.0");

            service.saveRecordAfterPrediction(
                    patient(1), doctor(1), "note", "plan",
                    BigDecimal.valueOf(20.0), "LOW", "explanation",
                    req, null, null);

            ArgumentCaptor<HeartClinicalMetrics> captor = ArgumentCaptor.forClass(HeartClinicalMetrics.class);
            verify(heartClinicalMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getFastingBloodSugar()).isFalse();
            assertThat(captor.getValue().getExerciseAngina()).isFalse();
        }

        @Test
        @DisplayName("fbs/exang = 'TRUE' (giá trị LỖI CŨ trước A.1, vẫn được chấp nhận tương thích ngược) -> true")
        void legacyTrueString_stillAccepted() {
            AIRequest req = baseAiRequest();
            req.setFbs("TRUE");
            req.setExang("TRUE");

            service.saveRecordAfterPrediction(
                    patient(1), doctor(1), "note", "plan",
                    BigDecimal.valueOf(45.0), "MEDIUM", "explanation",
                    req, null, null);

            ArgumentCaptor<HeartClinicalMetrics> captor = ArgumentCaptor.forClass(HeartClinicalMetrics.class);
            verify(heartClinicalMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getFastingBloodSugar()).isTrue();
            assertThat(captor.getValue().getExerciseAngina()).isTrue();
        }

        @Test
        @DisplayName("fbs = null -> lưu thành false (không crash)")
        void nullValue_defaultsFalse() {
            AIRequest req = baseAiRequest();
            req.setFbs(null);
            req.setExang(null);

            service.saveRecordAfterPrediction(
                    patient(1), doctor(1), "note", "plan",
                    BigDecimal.valueOf(20.0), "LOW", "explanation",
                    req, null, null);

            ArgumentCaptor<HeartClinicalMetrics> captor = ArgumentCaptor.forClass(HeartClinicalMetrics.class);
            verify(heartClinicalMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getFastingBloodSugar()).isFalse();
            assertThat(captor.getValue().getExerciseAngina()).isFalse();
        }
    }

    // ════════════════════════════════════════════════════════════
    // [A.4] Test lưu topFactorsJson/trendInfoJson
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Lưu SHAP top_factors / trend vào DB (A.4)")
    class TopFactorsAndTrendStorage {

        @Test
        @DisplayName("saveRecordAfterPrediction lưu đúng topFactorsJson/trendInfoJson đã truyền vào")
        void savesProvidedJsonStrings() {
            String topFactorsJson = "[{\"feature\":\"Tuổi\",\"direction\":\"↓ giảm\"}]";
            String trendInfoJson = "{\"trend\":\"DECREASING\",\"trend_message\":\"Giảm 5%\"}";

            AIRiskPrediction result = service.saveRecordAfterPrediction(
                    patient(1), doctor(1), "note", "plan",
                    BigDecimal.valueOf(30.0), "LOW", "explanation",
                    baseAiRequest(), topFactorsJson, trendInfoJson);

            assertThat(result.getTopFactorsJson()).isEqualTo(topFactorsJson);
            assertThat(result.getTrendInfoJson()).isEqualTo(trendInfoJson);
        }

        @Test
        @DisplayName("topFactorsJson/trendInfoJson = null (AI lỗi, chưa có SHAP) -> lưu null, không crash")
        void nullJson_doesNotCrash() {
            AIRiskPrediction result = service.saveRecordAfterPrediction(
                    patient(1), doctor(1), "note", "plan",
                    BigDecimal.valueOf(30.0), "LOW", "explanation",
                    baseAiRequest(), null, null);

            assertThat(result.getTopFactorsJson()).isNull();
            assertThat(result.getTrendInfoJson()).isNull();
        }

        @Test
        @DisplayName("createRecordWithAI tự serialize top_factors từ AIResponse thật")
        void createRecordWithAI_serializesFromAiResponse() {
            AIResponse.TopFactor factor = new AIResponse.TopFactor();
            factor.setFeature("Tuổi");
            factor.setDirection("↓ giảm");
            factor.setImpact(0.05);
            factor.setClinical("Tuổi còn trẻ");
            factor.setValue(35);

            AIResponse aiResponse = new AIResponse();
            aiResponse.setProbability(0.25);
            aiResponse.setRisk_level("LOW");
            aiResponse.setMessage("Nguy cơ thấp");
            aiResponse.setTop_factors(List.of(factor));
            aiResponse.setTrend("UNKNOWN"); // chưa đủ lịch sử -> không nên lưu trend

            when(aiService.predict(any())).thenReturn(aiResponse);

            AIRiskPrediction result = service.createRecordWithAI(
                    patient(1), doctor(1), "note", "plan", baseAiRequest());

            assertThat(result.getTopFactorsJson()).contains("Tuổi").contains("giảm");
            // trend = "UNKNOWN" KHÔNG nên được lưu (chưa đủ lịch sử thật)
            assertThat(result.getTrendInfoJson()).isNull();
        }
    }

    // ════════════════════════════════════════════════════════════
    // [A.5] Test exceedsPersonalThreshold
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Ngưỡng cảnh báo cá nhân hoá (A.5)")
    class PersonalThreshold {

        @Test
        @DisplayName("riskScore vượt ngưỡng riêng đã cấu hình -> true")
        void exceedsThreshold_returnsTrue() {
            PatientProfile p = patient(1);
            DoctorProfile d = doctor(1);
            ConsultationRecord record = new ConsultationRecord();
            record.setPatient(p);
            record.setDoctor(d);

            AIRiskPrediction prediction = new AIRiskPrediction();
            prediction.setRecord(record);
            prediction.setRiskScore(BigDecimal.valueOf(45.0));

            PatientAlertThreshold threshold = new PatientAlertThreshold();
            threshold.setRiskScoreThreshold(30.0);
            when(thresholdRepository.findByPatientAndDoctor(p, d)).thenReturn(Optional.of(threshold));

            assertThat(service.exceedsPersonalThreshold(prediction)).isTrue();
        }

        @Test
        @DisplayName("riskScore dưới ngưỡng riêng -> false")
        void belowThreshold_returnsFalse() {
            PatientProfile p = patient(1);
            DoctorProfile d = doctor(1);
            ConsultationRecord record = new ConsultationRecord();
            record.setPatient(p);
            record.setDoctor(d);

            AIRiskPrediction prediction = new AIRiskPrediction();
            prediction.setRecord(record);
            prediction.setRiskScore(BigDecimal.valueOf(20.0));

            PatientAlertThreshold threshold = new PatientAlertThreshold();
            threshold.setRiskScoreThreshold(30.0);
            when(thresholdRepository.findByPatientAndDoctor(p, d)).thenReturn(Optional.of(threshold));

            assertThat(service.exceedsPersonalThreshold(prediction)).isFalse();
        }

        @Test
        @DisplayName("Bác sĩ CHƯA cấu hình ngưỡng riêng -> false (không suy đoán hộ)")
        void noThresholdConfigured_returnsFalse() {
            PatientProfile p = patient(1);
            DoctorProfile d = doctor(1);
            ConsultationRecord record = new ConsultationRecord();
            record.setPatient(p);
            record.setDoctor(d);

            AIRiskPrediction prediction = new AIRiskPrediction();
            prediction.setRecord(record);
            prediction.setRiskScore(BigDecimal.valueOf(95.0)); // dù risk rất cao

            when(thresholdRepository.findByPatientAndDoctor(p, d)).thenReturn(Optional.empty());

            assertThat(service.exceedsPersonalThreshold(prediction)).isFalse();
        }

        @Test
        @DisplayName("record.doctor = null (chưa gán bác sĩ) -> false, không NullPointerException")
        void nullDoctor_doesNotThrow() {
            ConsultationRecord record = new ConsultationRecord();
            record.setPatient(patient(1));
            record.setDoctor(null);

            AIRiskPrediction prediction = new AIRiskPrediction();
            prediction.setRecord(record);
            prediction.setRiskScore(BigDecimal.valueOf(95.0));

            assertThat(service.exceedsPersonalThreshold(prediction)).isFalse();
        }

        @Test
        @DisplayName("prediction.record = null -> false, không NullPointerException")
        void nullRecord_doesNotThrow() {
            AIRiskPrediction prediction = new AIRiskPrediction();
            prediction.setRecord(null);

            assertThat(service.exceedsPersonalThreshold(prediction)).isFalse();
        }
    }

    // ════════════════════════════════════════════════════════════
    // Test calcUrgencyScore (đã có sẵn trước A.5, bổ sung test cho chắc)
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Tính điểm độ khẩn cấp")
    class UrgencyScore {

        @Test
        @DisplayName("Đã xử lý (isAlertSent=true) -> urgency = 0 bất kể riskScore")
        void handledAlert_zeroUrgency() {
            AIRiskPrediction prediction = new AIRiskPrediction();
            prediction.setIsAlertSent(true);
            prediction.setRiskScore(BigDecimal.valueOf(99.0));

            assertThat(service.calcUrgencyScore(prediction)).isZero();
        }

        @Test
        @DisplayName("Chưa xử lý, mới khám hôm nay -> urgency = riskScore (chưa cộng bonus ngày chờ)")
        void freshUnhandledAlert_equalsRiskScore() {
            ConsultationRecord record = new ConsultationRecord();
            record.setVisitDate(java.time.LocalDateTime.now());

            AIRiskPrediction prediction = new AIRiskPrediction();
            prediction.setIsAlertSent(false);
            prediction.setRiskScore(BigDecimal.valueOf(50.0));
            prediction.setRecord(record);

            assertThat(service.calcUrgencyScore(prediction)).isEqualTo(50.0);
        }
    }

    // ════════════════════════════════════════════════════════════
    // Trường hợp biên: lịch sử rỗng / dữ liệu thiếu
    // ════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Trường hợp biên: lịch sử rỗng, dữ liệu thiếu")
    class EdgeCases {

        @Test
        @DisplayName("getByPatient với bệnh nhân chưa có lịch sử khám -> trả về danh sách rỗng, không crash")
        void noConsultationHistory_returnsEmptyList() {
            PatientProfile p = patient(1);
            when(consultationRepository.findByPatientOrderByVisitDateDesc(p))
                    .thenReturn(List.of());

            List<ConsultationRecord> result = service.getByPatient(p);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getByPatientChronological đảo ngược đúng thứ tự danh sách rỗng -> vẫn rỗng, không lỗi")
        void chronological_emptyList_noError() {
            PatientProfile p = patient(1);
            when(consultationRepository.findByPatientOrderByVisitDateDesc(p))
                    .thenReturn(new java.util.ArrayList<>());

            List<ConsultationRecord> result = service.getByPatientChronological(p);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AIRequest thiếu hầu hết field optional (chỉ có age/sex/cp bắt buộc) -> lưu HeartClinicalMetrics không crash")
        void mostFieldsNull_doesNotCrash() {
            AIRequest req = new AIRequest();
            req.setAge(40.0);
            req.setSex("Female");
            req.setCp("non-anginal");
            // toàn bộ field còn lại để null

            AIRiskPrediction result = service.saveRecordAfterPrediction(
                    patient(1), doctor(1), "note", null,
                    BigDecimal.valueOf(10.0), "LOW", "explanation",
                    req, null, null);

            assertThat(result).isNotNull();
            verify(heartClinicalMetricsRepository).save(any());
        }

        @Test
        @DisplayName("doctor = null (chưa gán bác sĩ cụ thể) -> record.doctor được lưu null, không NPE")
        void nullDoctorOnSave_doesNotThrow() {
            AIRiskPrediction result = service.saveRecordAfterPrediction(
                    patient(1), null, "note", "plan",
                    BigDecimal.valueOf(10.0), "LOW", "explanation",
                    baseAiRequest(), null, null);

            assertThat(result).isNotNull();
            assertThat(result.getRecord().getDoctor()).isNull();
        }
    }
}