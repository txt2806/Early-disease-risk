package com.cardio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import java.io.Serializable;

@Data
@Embeddable
public class RecordIcdKey implements Serializable {

    @Column(name = "RecordID")
    private Integer recordId;

    @Column(name = "ICDCode")
    private String icdCode;
}
