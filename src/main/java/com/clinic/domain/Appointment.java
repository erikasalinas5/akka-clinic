package com.clinic.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record Appointment(String id, LocalDateTime dateTime, String doctorId, String patientId, String issue,
                          Optional<String> notes, List<String> prescriptions, Optional<String> priority, Status status) {
    // Enums are lists of values
    public enum Status {
        PENDING,
        SCHEDULED,
        CANCELLED,
        COMPLETED,
        MISSED
    }

    public Appointment(String id, LocalDateTime dateTime, String doctorId, String patientId, String issue) {
        this(id, dateTime, doctorId, patientId, issue, Optional.empty(), List.of(), Optional.empty(), Status.PENDING);
    }

    public Appointment reschedule(LocalDateTime newDateTime, String newDoctorId) {
        return new Appointment(id, newDateTime, newDoctorId, patientId, issue, notes, prescriptions, priority, status);
    }

    public Appointment addNotes(String notes) {
        return new Appointment(id, dateTime, doctorId, patientId, issue, Optional.of(notes), prescriptions, priority, status);
    }

    public Appointment addPrescription(String prescription) {
        var prescriptions = new ArrayList<>(this.prescriptions);
        prescriptions.add(prescription);
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, Collections.unmodifiableList(prescriptions), priority, status);
    }

    public Appointment markAsScheduled() {
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, prescriptions, priority, Status.SCHEDULED);
    }

    public Appointment cancel() {
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, prescriptions, priority, Status.CANCELLED);
    }

    public Appointment complete() {
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, prescriptions, priority, Status.COMPLETED);
    }

    public Appointment markAsMissed() {
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, prescriptions, priority, Status.MISSED);
    }

    public Appointment addPriority(String priority) {
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, prescriptions, Optional.of(priority), status);
    }
}