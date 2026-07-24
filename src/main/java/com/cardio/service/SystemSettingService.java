package com.cardio.service;

import com.cardio.model.SystemSetting;
import com.cardio.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository repository;

    @Value("${clinic.fee.general:150000}")
    private Long defaultFeeGeneral;

    @Value("${clinic.fee.specialist:300000}")
    private Long defaultFeeSpecialist;

    @Value("${clinic.fee.lab:200000}")
    private Long defaultFeeLab;

    public Long getFeeGeneral() {
        return repository.findById("fee_general")
                .map(s -> {
                    try {
                        return Long.parseLong(s.getSettingValue());
                    } catch (NumberFormatException e) {
                        return defaultFeeGeneral;
                    }
                })
                .orElse(defaultFeeGeneral);
    }

    public Long getFeeSpecialist() {
        return repository.findById("fee_specialist")
                .map(s -> {
                    try {
                        return Long.parseLong(s.getSettingValue());
                    } catch (NumberFormatException e) {
                        return defaultFeeSpecialist;
                    }
                })
                .orElse(defaultFeeSpecialist);
    }

    public Long getFeeLab() {
        return repository.findById("fee_lab")
                .map(s -> {
                    try {
                        return Long.parseLong(s.getSettingValue());
                    } catch (NumberFormatException e) {
                        return defaultFeeLab;
                    }
                })
                .orElse(defaultFeeLab);
    }

    public void updateFeeGeneral(Long value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey("fee_general");
        setting.setSettingValue(String.valueOf(value));
        repository.save(setting);
    }

    public void updateFeeSpecialist(Long value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey("fee_specialist");
        setting.setSettingValue(String.valueOf(value));
        repository.save(setting);
    }

    public void updateFeeLab(Long value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey("fee_lab");
        setting.setSettingValue(String.valueOf(value));
        repository.save(setting);
    }
}
