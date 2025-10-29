package com.clinic.domain;

import akka.javasdk.annotations.TypeName;

import java.time.LocalDateTime;

public sealed interface AppointmentEvents {
    @TypeName("appointment-created")
    record AppointmentCreated(String id, LocalDateTime dateTime, String doctorId, String patientId,
                              String issue) implements AppointmentEvents {
    }

    @TypeName("added-doctor-notes")
    record AddedDoctorNotes(String notes) implements AppointmentEvents {
    }

    @TypeName("added-doctor-prescription")
    record AddedPrescription(String prescription) implements AppointmentEvents {
    }

    @TypeName("rescheduled")
    record Rescheduled(LocalDateTime dateTime, String doctorId) implements AppointmentEvents {
    }

    @TypeName("scheduled")
    record Scheduled() implements AppointmentEvents {
    }

    @TypeName("cancelled")
    record Cancelled() implements AppointmentEvents {
    }

    @TypeName("completed")
    record Completed() implements AppointmentEvents {
    }

    @TypeName("missed")
    record Missed() implements AppointmentEvents {
    }

    @TypeName("added-priority")
    record AddedPriority(String priority) implements AppointmentEvents {}
}