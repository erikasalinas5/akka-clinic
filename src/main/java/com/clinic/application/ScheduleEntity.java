package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

@Component(id = "schedule")
public class ScheduleEntity extends KeyValueEntity<Schedule> {
    private Schedule.ScheduleId entityId;

    public ScheduleEntity(KeyValueEntityContext context) {
        this.entityId = Schedule.ScheduleId.fromString(context.entityId());
    }

    public Effect<Done> createSchedule(Schedule.WorkingHours workingHours) {
        if (currentState() != null)
            return effects().error("Schedule already exists");

        var schedule = new Schedule(entityId, workingHours);
        return effects()
                .updateState(schedule)
                .thenReply(Done.getInstance());
    }

    public record ScheduleAppointmentData(LocalTime startTime, Duration duration, String appointmentId) {
    }



    public Effect<Done> scheduleAppointment(ScheduleAppointmentData data) {
        if (currentState() == null)
            return effects().error("Working hours aren't defined for the selected date");

        try {
            var newState = currentState()
                    .scheduleAppointment(data.startTime, data.duration, data.appointmentId);
            return effects().updateState(newState).thenReply(Done.getInstance());
        } catch (IllegalArgumentException e) {
            return effects().error(e.getMessage());
        }
    }
    public Effect<Done> cancelAppointmentByStartTime(LocalTime startTime) {
        if (currentState() == null)
            return effects().error("Schedule does not exist");

        try {
            var newState = currentState().removeTimeSlotByStartTime(startTime);
            return effects().updateState(newState).thenReply(Done.getInstance());
        } catch (IllegalArgumentException e) {
            return effects().error(e.getMessage());
        }
    }

    public Effect<Done> blockDay() {
        if (currentState() == null) return effects().error("Schedule does not exist");
        try {
            var newState = currentState().blockSchedule();
            return effects().updateState(newState).thenReply(Done.getInstance());
        } catch (IllegalArgumentException e) {
            return effects().error(e.getMessage());
        }
    }

    /** Doctor cancels the day (soft delete). Prevents new bookings; workflows can remove existing slots. */
    public Effect<Done> cancelDay() {
        if (currentState() == null) return effects().error("Schedule does not exist");
        try {
            var newState = currentState().cancelSchedule();
            return effects().updateState(newState).thenReply(Done.getInstance());
        } catch (IllegalArgumentException e) {
            return effects().error(e.getMessage());
        }
    }


    public Effect<Done> reactivateDay() {
        if (currentState() == null) return effects().error("Schedule does not exist");
        try {
            var s = currentState();
            var reopened = new Schedule(s.id(), s.workingHours(), s.timeSlots(), Schedule.Status.ACTIVE);
            return effects().updateState(reopened).thenReply(Done.getInstance());
        } catch (IllegalArgumentException e) {
            return effects().error(e.getMessage());
        }
    }



    public Effect<Optional<Schedule>> getSchedule() {
        return effects().reply(Optional.ofNullable(currentState()));
    }
}