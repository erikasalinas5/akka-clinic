package com.clinic.domain;

import java.time.LocalDateTime;

public record RescheduleAppointmentState(
        String appointmentId,
        LocalDateTime oldDateTime,
        String oldDoctorId,
        LocalDateTime newDateTime,
        String newDoctorId,
        Status status
) {

    public enum Status {
        Initial,
        NewSlotCreated,
        AppointmentRescheduled,
        OldSlotRemoved,
        Failed
    }

    /** Returns a new state with updated status. */
    public RescheduleAppointmentState withStatus(Status newStatus) {
        return new RescheduleAppointmentState(
                appointmentId,
                oldDateTime,
                oldDoctorId,
                newDateTime,
                newDoctorId,
                newStatus
        );
    }

    /** Returns a new state with old appointment data populated. */
    public RescheduleAppointmentState withOld(LocalDateTime oldDateTime, String oldDoctorId) {
        return new RescheduleAppointmentState(
                appointmentId,
                oldDateTime,
                oldDoctorId,
                newDateTime,
                newDoctorId,
                status
        );
    }

    /** Factory method for initial state. */
    public static RescheduleAppointmentState initial(String appointmentId, LocalDateTime newDateTime, String newDoctorId) {
        return new RescheduleAppointmentState(
                appointmentId,
                null,
                null,
                newDateTime,
                newDoctorId,
                Status.Initial
        );
    }
}
