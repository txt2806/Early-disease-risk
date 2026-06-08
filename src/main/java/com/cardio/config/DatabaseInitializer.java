package com.cardio.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            createMissingTables();
            log.info("Database initialization check completed successfully.");
        } catch (Exception e) {
            log.error("Error during database initialization check: ", e);
        }
    }

    private void createMissingTables() {
        log.info("Creating missing tables for H2 compatibility if not exist...");
        
        // 1. ICD_Catalog
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ICD_Catalog (" +
                    "ICDCode VARCHAR(20) PRIMARY KEY, " +
                    "DiseaseName VARCHAR(255) NOT NULL)");
            log.info("Table ICD_Catalog is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create ICD_Catalog: {}", e.getMessage());
        }

        // 2. Appointment
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS Appointment (" +
                    "AppointmentID SERIAL PRIMARY KEY, " +
                    "PatientID INT, " +
                    "DoctorID INT, " +
                    "ScheduledDate DATE NOT NULL, " +
                    "TimeSlot TIME NOT NULL, " +
                    "Status VARCHAR(20) DEFAULT 'Pending')");
            log.info("Table Appointment is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create Appointment: {}", e.getMessage());
        }

        // 3. Heart_Clinical_Metrics
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS Heart_Clinical_Metrics (" +
                    "MetricID SERIAL PRIMARY KEY, " +
                    "RecordID INT UNIQUE, " +
                    "RecordedBy_StaffID INT, " +
                    "ChestPainType INT, " +
                    "RestingBP INT, " +
                    "Cholesterol INT, " +
                    "FastingBloodSugar BOOLEAN, " +
                    "RestingECG INT, " +
                    "MaxHeartRate INT, " +
                    "ExerciseAngina BOOLEAN, " +
                    "RecordedAt TIMESTAMP NOT NULL)");
            log.info("Table Heart_Clinical_Metrics is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create Heart_Clinical_Metrics: {}", e.getMessage());
        }

        // 4. Patient_Self_Monitoring
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS Patient_Self_Monitoring (" +
                    "LogID SERIAL PRIMARY KEY, " +
                    "PatientID INT, " +
                    "LogDate TIMESTAMP NOT NULL, " +
                    "CurrentHeartRate INT, " +
                    "Symptoms VARCHAR(255), " +
                    "TriggeredAlert BOOLEAN DEFAULT FALSE)");
            log.info("Table Patient_Self_Monitoring is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create Patient_Self_Monitoring: {}", e.getMessage());
        }

        // 5. Record_ICD
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS Record_ICD (" +
                    "RecordID INT, " +
                    "ICDCode VARCHAR(20), " +
                    "Notes VARCHAR(255), " +
                    "PRIMARY KEY (RecordID, ICDCode))");
            log.info("Table Record_ICD is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create Record_ICD: {}", e.getMessage());
        }
    }
}
