package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "System_Log")
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LogID")
    private Integer logId;

    @Column(name = "Username", nullable = false)
    private String username;

    @Column(name = "Action", nullable = false)
    private String action;

    @Column(name = "Details", columnDefinition = "NVARCHAR(MAX)")
    private String details;

    @Column(name = "Timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}
