package com.clinic.domain;

import java.time.LocalDateTime;

public record ReassignAppointmentState (
    String appointmentId,
    LocalDateTime oldDateTime,
    String oldDoctorId,
    LocalDateTime newDateTime,
    String newDoctorId,
    Status status
){
    public enum Status {
        Initial,
        appointmentAsPending,
        doctorsFound,
        availabilityChecked,
        timeSlotCreated,
        appointmentUpdated,
        newTimeSlotDeleted,
        oldTimeSlotDeleted,
        Failed
    }
    public ReassignAppointmentState withStatus(Status newStatus){
        return new ReassignAppointmentState(appointmentId, oldDateTime, oldDoctorId, newDateTime, newDoctorId, newStatus);
    }


}
