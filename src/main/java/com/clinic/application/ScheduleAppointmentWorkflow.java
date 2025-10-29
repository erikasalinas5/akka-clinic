package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.clinic.domain.ScheduleAppointmentState;
import com.clinic.domain.Schedule;
import java.time.Duration;
import java.time.LocalDateTime;

@Component(id = "schedule-appointment")
public class ScheduleAppointmentWorkflow extends Workflow<ScheduleAppointmentState> {

    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);

    private final ComponentClient componentClient;

    public ScheduleAppointmentWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record ScheduleAppointmentCommand(LocalDateTime dateTime, String doctorId, String patientId, String issue) {}

    public Effect<Done> schedule(ScheduleAppointmentCommand cmd) {
        System.out.println("## schedule");
        if (currentState() != null)
            return effects().error("Appointment already exists");

        var state = new ScheduleAppointmentState(cmd.dateTime, cmd.doctorId, cmd.patientId, cmd.issue, ScheduleAppointmentState.Status.Initial);
        return effects()
                .updateState(state)
                .transitionTo(ScheduleAppointmentWorkflow::createAppointment)
                .thenReply(Done.getInstance());
    }

    public Effect<ScheduleAppointmentState> getState() {
        return effects().reply(currentState());
    }

    public Effect<Boolean> isCompleted() {
        return effects()
                .reply(currentState().status() == ScheduleAppointmentState.Status.AppointmentScheduled || currentState().status() == ScheduleAppointmentState.Status.AppointmentCancelled);
    }

    public StepEffect createAppointment() {
        System.out.println("## createAppointment");
        componentClient
                .forEventSourcedEntity(commandContext().workflowId())
                .method(AppointmentEntity::createAppointment)
                .invoke(new AppointmentEntity.CreateAppointmentCmd(currentState().dateTime(), currentState().doctorId(), currentState().patientId(), currentState().issue()));

        return stepEffects()
                .updateState(currentState().withStatus(ScheduleAppointmentState.Status.AppointmentCreated))
                .thenTransitionTo(ScheduleAppointmentWorkflow::scheduleTimeSlot);
    }

    public StepEffect scheduleTimeSlot() {
        System.out.println("## scheduleTimeSlot");
        var scheduleId = new Schedule.ScheduleId(currentState().doctorId(), currentState().dateTime().toLocalDate());
        try {
            componentClient
                    .forKeyValueEntity(scheduleId.toString())
                    .method(ScheduleEntity::scheduleAppointment)
                    .invoke(new ScheduleEntity.ScheduleAppointmentData(currentState().dateTime().toLocalTime(), DEFAULT_DURATION, commandContext().workflowId()));
        } catch (IllegalArgumentException e) {
            return stepEffects()
                    .thenTransitionTo(ScheduleAppointmentWorkflow::cancelAppointment);
        }

        return stepEffects()
                .updateState(currentState().withStatus(ScheduleAppointmentState.Status.TimeSlotScheduled))
                .thenTransitionTo(ScheduleAppointmentWorkflow::markAppointmentAsScheduled);
    }

    public StepEffect markAppointmentAsScheduled() {
        System.out.println("## markAppointmentAsScheduled");
        componentClient
                .forEventSourcedEntity(commandContext().workflowId())
                .method(AppointmentEntity::schedule)
                .invoke();

        return stepEffects()
                .updateState(currentState().withStatus(ScheduleAppointmentState.Status.AppointmentScheduled))
                .thenEnd();
    }

    public StepEffect cancelAppointment() {
        System.out.println("## cancelAppointment");
        componentClient
                .forEventSourcedEntity(commandContext().workflowId())
                .method(AppointmentEntity::cancel)
                .invoke();

        return stepEffects()
                .updateState(currentState().withStatus(ScheduleAppointmentState.Status.AppointmentCancelled))
                .thenEnd();
    }

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettingsBuilder
                .newBuilder()
                .stepTimeout(ScheduleAppointmentWorkflow::scheduleTimeSlot, Duration.ofSeconds(40))
                .defaultStepRecovery(RecoverStrategy.maxRetries(2).failoverTo(ScheduleAppointmentWorkflow::cancelAppointment))
                .build();
    }
}