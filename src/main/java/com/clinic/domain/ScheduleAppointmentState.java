package com.clinic.domain;

import java.time.LocalDateTime;

public record ScheduleAppointmentState(LocalDateTime dateTime, String doctorId, String patientId, String issue, Status status) {

    public enum Status {
        Initial,
        AppointmentCreated,
        TimeSlotScheduled,
        AppointmentCancelled,
        AppointmentScheduled
    }

    public ScheduleAppointmentState withStatus(Status status) {
        return new ScheduleAppointmentState(dateTime, doctorId, patientId, issue, status);
    }

}