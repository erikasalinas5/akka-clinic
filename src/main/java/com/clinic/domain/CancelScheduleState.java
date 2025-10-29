package com.clinic.domain;

import akka.actor.Status;

import java.time.LocalDateTime;

public record CancelScheduleState (LocalDateTime dateTime, String doctorId, Status status){
    public enum Status {
        Initial,
        cancelApproved,
        scheduleBlocked,
        appointmentsRescheduled,
        scheduleCancelled,
        Failed
    }
    public CancelScheduleState withStatus(Status status){
        return new CancelScheduleState(dateTime, doctorId, status);
    }
}
