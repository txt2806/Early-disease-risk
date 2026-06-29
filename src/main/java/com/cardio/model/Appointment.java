package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AppointmentID")
    private Integer appointmentId;

    @ManyToOne
    @JoinColumn(name = "PatientID")
    private PatientProfile patient;

    @ManyToOne
    @JoinColumn(name = "DoctorID")
    private DoctorProfile doctor;

    @Column(name = "ScheduledDate", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "TimeSlot")
    private LocalTime timeSlot;

    @Column(name = "EndTime")
    private LocalTime endTime;

    @Column(name = "RequestTime")
    private LocalDateTime requestTime = LocalDateTime.now();
    @Column(name = "Status", nullable = false)
    private String status = "Pending"; // Pending, Confirmed, CheckedIn, InProgress, Completed, Cancelled

    @Column(name = "RoomNumber")
    private String roomNumber;

    @Column(name = "PreliminaryStatus")
    private String preliminaryStatus;
    @Transient
    private Integer queueNumber;

    public static void calculateQueueNumbers(java.util.List<Appointment> list) {
        if (list == null) return;
        java.util.Map<String, java.util.List<Appointment>> groups = new java.util.HashMap<>();
        for (Appointment a : list) {
            if (a.getDoctor() != null && a.getScheduledDate() != null) {
                String key = a.getDoctor().getDoctorId() + "_" + a.getScheduledDate().toString();
                groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(a);
            }
        }
        for (java.util.List<Appointment> group : groups.values()) {
            group.sort((a1, a2) -> {
                int p1 = getStatusPriority(a1.getStatus());
                int p2 = getStatusPriority(a2.getStatus());
                if (p1 != p2) return Integer.compare(p1, p2);
                if (p1 == 1) { // CheckedIn FIFO by arrival time slot
                    if (a1.getTimeSlot() != null && a2.getTimeSlot() != null) {
                        return a1.getTimeSlot().compareTo(a2.getTimeSlot());
                    }
                    if (a1.getTimeSlot() != null) return -1;
                    if (a2.getTimeSlot() != null) return 1;
                }
                // Fallback to id
                if (a1.getAppointmentId() != null && a2.getAppointmentId() != null) {
                    return a1.getAppointmentId().compareTo(a2.getAppointmentId());
                }
                return 0;
            });
            int q = 1;
            for (Appointment a : group) {
                if (!"Cancelled".equalsIgnoreCase(a.getStatus())) {
                    a.setQueueNumber(q++);
                } else {
                    a.setQueueNumber(null);
                }
            }
        }
    }

    private static int getStatusPriority(String status) {
        if ("InProgress".equalsIgnoreCase(status)) return 0;
        if ("CheckedIn".equalsIgnoreCase(status)) return 1;
        if ("Confirmed".equalsIgnoreCase(status)) return 2;
        if ("Pending".equalsIgnoreCase(status)) return 3;
        if ("Completed".equalsIgnoreCase(status)) return 4;
        return 5; // Cancelled
    }

    public static void populateQueueNumbers(java.util.List<Appointment> targetList, java.util.List<Appointment> referenceList) {
        calculateQueueNumbers(referenceList);
        java.util.Map<Integer, Integer> queueMap = new java.util.HashMap<>();
        for (Appointment a : referenceList) {
            if (a.getQueueNumber() != null) {
                queueMap.put(a.getAppointmentId(), a.getQueueNumber());
            }
        }
        for (Appointment a : targetList) {
            a.setQueueNumber(queueMap.get(a.getAppointmentId()));
        }
    }
}
