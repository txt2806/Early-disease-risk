package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Appointment", indexes = {
    @Index(name = "idx_appointment_date", columnList = "ScheduledDate"),
    @Index(name = "idx_appointment_status", columnList = "Status")
})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AppointmentID")
    private Integer appointmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PatientID")
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY)
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
    @Column(name = "bookingtype")
    private String bookingType = "General"; // General or Specialist

    @Transient
    private Integer queueNumber;

    @Transient
    private Integer activeQueueNumber;

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
            // Sort all non-cancelled appointments by slot (timeSlot) then booking time (requestTime)
            java.util.List<Appointment> nonCancelled = group.stream()
                .filter(a -> !"Cancelled".equalsIgnoreCase(a.getStatus()))
                .sorted((a1, a2) -> {
                    LocalTime t1 = a1.getTimeSlot();
                    LocalTime t2 = a2.getTimeSlot();
                    if (t1 != null && t2 != null) {
                        int comp = t1.compareTo(t2);
                        if (comp != 0) return comp;
                    } else if (t1 != null) {
                        return -1;
                    } else if (t2 != null) {
                        return 1;
                    }
                    LocalDateTime r1 = a1.getRequestTime();
                    LocalDateTime r2 = a2.getRequestTime();
                    if (r1 != null && r2 != null) {
                        int comp = r1.compareTo(r2);
                        if (comp != 0) return comp;
                    } else if (r1 != null) {
                        return -1;
                    } else if (r2 != null) {
                        return 1;
                    }
                    if (a1.getAppointmentId() != null && a2.getAppointmentId() != null) {
                        return a1.getAppointmentId().compareTo(a2.getAppointmentId());
                    }
                    return 0;
                })
                .collect(java.util.stream.Collectors.toList());

            int q = 1;
            for (Appointment a : nonCancelled) {
                a.setQueueNumber(q++);
            }

            // Set cancelled queueNumber to null
            group.stream()
                .filter(a -> "Cancelled".equalsIgnoreCase(a.getStatus()))
                .forEach(a -> a.setQueueNumber(null));

            // Calculate active queue numbers for InProgress and CheckedIn
            java.util.List<Appointment> activeList = nonCancelled.stream()
                .filter(a -> "InProgress".equalsIgnoreCase(a.getStatus()) || "CheckedIn".equalsIgnoreCase(a.getStatus()))
                .sorted(java.util.Comparator.comparing(Appointment::getQueueNumber))
                .collect(java.util.stream.Collectors.toList());

            int aq = 1;
            for (Appointment a : activeList) {
                a.setActiveQueueNumber(aq++);
            }

            // For other appointments, set activeQueueNumber to null
            group.stream()
                .filter(a -> !"InProgress".equalsIgnoreCase(a.getStatus()) && !"CheckedIn".equalsIgnoreCase(a.getStatus()))
                .forEach(a -> a.setActiveQueueNumber(null));
        }
    }

    public static void populateQueueNumbers(java.util.List<Appointment> targetList, java.util.List<Appointment> referenceList) {
        calculateQueueNumbers(referenceList);
        java.util.Map<Integer, Integer> queueMap = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> activeQueueMap = new java.util.HashMap<>();
        for (Appointment a : referenceList) {
            if (a.getQueueNumber() != null) {
                queueMap.put(a.getAppointmentId(), a.getQueueNumber());
            }
            if (a.getActiveQueueNumber() != null) {
                activeQueueMap.put(a.getAppointmentId(), a.getActiveQueueNumber());
            }
        }
        for (Appointment a : targetList) {
            a.setQueueNumber(queueMap.get(a.getAppointmentId()));
            a.setActiveQueueNumber(activeQueueMap.get(a.getAppointmentId()));
        }
    }
}
