package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Patient_Alert_Threshold",
       uniqueConstraints = @UniqueConstraint(columnNames = {"PatientID", "DoctorID"}))
public class PatientAlertThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer thresholdId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PatientID", nullable = false)
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DoctorID", nullable = false)
    private DoctorProfile doctor;

    // Ngưỡng nguy cơ AI (%) — AI cảnh báo khi vượt mức này
    @Column(name = "RiskScoreThreshold")
    private Double riskScoreThreshold = 40.0; // mặc định 40%

    // ── Nhịp tim (bpm) ───────────────────────────────────
    @Column(name = "MaxBpm")
    private Integer maxBpm = 100;

    // [MỚI] Nhịp tim tối thiểu — cảnh báo khi nhịp tim QUÁ THẤP (nhịp
    // chậm bất thường, bradycardia), không chỉ quá cao. Mặc định 50 bpm
    // (dưới ngưỡng này ở người lớn khi nghỉ thường được coi là bất thường
    // trừ khi là vận động viên).
    @Column(name = "MinBpm")
    private Integer minBpm = 50;

    // ── Huyết áp tâm thu (mmHg) ──────────────────────────
    @Column(name = "MaxSystolicBp")
    private Integer maxSystolicBp = 140;

    // [MỚI] Huyết áp tâm thu tối thiểu — cảnh báo khi huyết áp QUÁ THẤP
    // (hạ huyết áp, nguy cơ choáng/ngất). Mặc định 90 mmHg (ngưỡng dưới
    // thường dùng trong lâm sàng để xác định hạ huyết áp).
    @Column(name = "MinSystolicBp")
    private Integer minSystolicBp = 90;

    // [MỚI] Cholesterol tối đa (mg/dL) — cảnh báo khi vượt ngưỡng nguy cơ
    // tim mạch. Mặc định 240 mg/dL (mức "cao" theo phân loại NCEP/AHA,
    // 200-239 là "borderline", >240 là "cao").
    @Column(name = "MaxCholesterol")
    private Integer maxCholesterol = 240;

    // Ghi chú bác sĩ về lý do đặt ngưỡng này
    @Column(name = "Notes", columnDefinition = "TEXT")
    private String notes;
}