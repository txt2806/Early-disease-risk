package com.cardio.dto;

import lombok.Data;

@Data
public class MedicalMetricsDTO {

    private String patientId;
    private int age;
    private String gender;
    private int chestPainType;
    private int restingBloodPressure;
    private int cholesterol;
    private int maxHeartRate;

}