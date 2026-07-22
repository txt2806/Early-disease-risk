package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "system_setting")
public class SystemSetting {

    @Id
    @Column(name = "settingkey", length = 100)
    private String settingKey;

    @Column(name = "settingvalue", nullable = false)
    private String settingValue;
}
