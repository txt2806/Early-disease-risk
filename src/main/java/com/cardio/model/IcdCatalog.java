package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "ICD_Catalog")
public class IcdCatalog {

    @Id
    @Column(name = "ICDCode")
    private String icdCode;

    @Column(name = "DiseaseName", nullable = false)
    private String diseaseName;
}
