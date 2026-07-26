package com.cardio.dto;

import com.cardio.model.PatientProfile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatientListDTO {
    private PatientProfile patient;
    private LocalDateTime lastVisitDate;
    private String doctorName;
    private String aiRisk;
}
