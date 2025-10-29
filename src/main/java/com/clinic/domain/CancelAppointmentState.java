package com.clinic.domain;

import java.time.LocalDateTime;

public record CancelAppointmentState (
        String appointmentId,
        LocalDateTime dateTime,
        String DoctorId,
        Status status
) {
    public enum Status {
        Initial,
        AppointmentCancelled,
        SlotDeleted,
        Failed
    }
    public CancelAppointmentState withStatus(Status status) {
        return new CancelAppointmentState(appointmentId, dateTime, DoctorId, status);
    }
}
