package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "InvoiceID")
    private Integer invoiceId;

    @OneToOne
    @JoinColumn(name = "AppointmentID")
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "PatientID")
    private PatientProfile patient;

    @Column(name = "Amount", nullable = false)
    private Long amount;

    @Column(name = "PaidAmount", nullable = false)
    private Long paidAmount = 0L;

    @Column(name = "PaymentMethod")
    private String paymentMethod; // Cash (Tiền mặt), Bank Transfer (Chuyển khoản)

    @Column(name = "Status", nullable = false)
    private String status = "Unpaid"; // Unpaid, PartiallyPaid, Paid

    @Column(name = "ReferenceCode")
    private String referenceCode; // "TT" + invoiceId

    @Column(name = "CreatedDate", nullable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "PaymentDate")
    private LocalDateTime paymentDate;
}
