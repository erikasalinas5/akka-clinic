package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.clinic.domain.Appointment;
import com.clinic.domain.RescheduleAppointmentState;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Component(id = "reschedule-appointment")
public class RescheduleAppointmentWorkflow extends Workflow<RescheduleAppointmentState> {

    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);

    private final ComponentClient componentClient;

    public RescheduleAppointmentWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record RescheduleAppointmentCommand(String appointmentId, LocalDateTime newDateTime, String newDoctorId) {}


    public Effect<Done> reschedule(RescheduleAppointmentCommand cmd) {
        if (currentState() != null) {
            return effects().error("Reschedule already in progress or finished for this workflow");
        }

        var init = RescheduleAppointmentState.initial(
                cmd.appointmentId(),     // use the appointment ID from command
                cmd.newDateTime(),
                cmd.newDoctorId()
        );

        return effects()
                .updateState(init)
                .transitionTo(RescheduleAppointmentWorkflow::loadCurrentAppointment)
                .thenReply(Done.getInstance());
    }


    public Effect<RescheduleAppointmentState> getState() {
        return effects().reply(currentState());
    }

    public StepEffect loadCurrentAppointment() {
        System.out.println("Reschedule");
        var apptId = currentState().appointmentId();
        Optional<Appointment> maybeAppt =
                componentClient
                        .forEventSourcedEntity(apptId)
                        .method(AppointmentEntity::getAppointment)
                        .invoke();

        if (maybeAppt.isEmpty()) {
            return stepEffects()
                    .updateState(currentState().withStatus(RescheduleAppointmentState.Status.Failed))
                    .thenEnd();
        }

        var appt = maybeAppt.get();
        var updated = currentState().withOld(appt.dateTime(), appt.doctorId());

        return stepEffects()
                .updateState(updated)
                .thenTransitionTo(RescheduleAppointmentWorkflow::createNewTimeSlot);
    }
    /** 1) Try to create the NEW time slot. If it fails, stop (no further changes). */
    public StepEffect createNewTimeSlot() {
        System.out.println("## reschedule.createNewTimeSlot");

        var newScheduleId = new Schedule.ScheduleId(
                currentState().newDoctorId(),
                currentState().newDateTime().toLocalDate()
        );

        try {
            componentClient
                    .forKeyValueEntity(newScheduleId.toString())
                    .method(ScheduleEntity::scheduleAppointment)
                    .invoke(new ScheduleEntity.ScheduleAppointmentData(
                            currentState().newDateTime().toLocalTime(),
                            DEFAULT_DURATION,
                            currentState().appointmentId()
                    ));
        } catch (IllegalArgumentException e) {
            // Could not allocate the new slot → end
            return stepEffects()
                    .updateState(currentState().withStatus(RescheduleAppointmentState.Status.Failed))
                    .thenEnd();
        }

        return stepEffects()
                .updateState(currentState().withStatus(RescheduleAppointmentState.Status.NewSlotCreated))
                .thenTransitionTo(RescheduleAppointmentWorkflow::rescheduleAppointmentEntity);
    }

    /**
     * 2) Reschedule AppointmentEntity to new doctor/dateTime.
     * If this fails, rollback the NEW time slot and stop.
     */
    public StepEffect rescheduleAppointmentEntity() {
        System.out.println("## reschedule.rescheduleAppointmentEntity");

        try {
            componentClient
                    .forEventSourcedEntity(currentState().appointmentId())
                    .method(AppointmentEntity::reschedule)
                    .invoke(new AppointmentEntity.RescheduleCmd(
                            currentState().newDateTime(),
                            currentState().newDoctorId()
                    ));
        } catch (IllegalArgumentException e) {
            // Rollback the newly created slot, best-effort
            rollbackNewTimeSlot();
            return stepEffects()
                    .updateState(currentState().withStatus(RescheduleAppointmentState.Status.Failed))
                    .thenEnd();
        }

        return stepEffects()
                .updateState(currentState().withStatus(RescheduleAppointmentState.Status.AppointmentRescheduled))
                .thenTransitionTo(RescheduleAppointmentWorkflow::removeOldTimeSlot);
    }

    /**
     * 3) Remove the OLD time slot (identified by old doctor/date + old start time).
     * If this fails (contention, transient), let workflow recovery handle retries.
     */
    public StepEffect removeOldTimeSlot() {
        System.out.println("## reschedule.removeOldTimeSlot");

        var oldScheduleId = new Schedule.ScheduleId(
                currentState().oldDoctorId(),
                currentState().oldDateTime().toLocalDate()
        );
        LocalTime oldStart = currentState().oldDateTime().toLocalTime();

        try {
            componentClient
                    .forKeyValueEntity(oldScheduleId.toString())
                    .method(ScheduleEntity::cancelAppointmentByStartTime)
                    .invoke(oldStart);
        } catch (IllegalArgumentException e) {
            // surface to recovery policy
            throw e;
        }

        return stepEffects()
                .updateState(currentState().withStatus(RescheduleAppointmentState.Status.OldSlotRemoved))
                .thenEnd();
    }

    // ===== Helpers =====

    /** Best-effort rollback of the NEW slot created in step 1. */
    private void rollbackNewTimeSlot() {
        var newScheduleId = new Schedule.ScheduleId(
                currentState().newDoctorId(),
                currentState().newDateTime().toLocalDate()
        );
        var start = currentState().newDateTime().toLocalTime();
        try {
            componentClient
                    .forKeyValueEntity(newScheduleId.toString())
                    .method(ScheduleEntity::cancelAppointmentByStartTime)
                    .invoke(start);
        } catch (Exception ignore) {
            // best-effort rollback only
        }
    }

    // ===== Settings =====

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettingsBuilder
                .newBuilder()
                // Removing the old slot can be contended; give it time + retries.
                .stepTimeout(RescheduleAppointmentWorkflow::removeOldTimeSlot, Duration.ofSeconds(40))
                .defaultStepRecovery(
                        RecoverStrategy.maxRetries(3)
                                .failoverTo(RescheduleAppointmentWorkflow::removeOldTimeSlot)
                )
                .build();
    }
}
