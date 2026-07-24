package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Record_ICD")
public class RecordIcd {

    @EmbeddedId
    private RecordIcdKey id = new RecordIcdKey();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("recordId")
    @JoinColumn(name = "RecordID")
    private ConsultationRecord record;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("icdCode")
    @JoinColumn(name = "ICDCode")
    private IcdCatalog icdCatalog;

    @Column(name = "Notes", columnDefinition = "TEXT")
    private String notes;
}
