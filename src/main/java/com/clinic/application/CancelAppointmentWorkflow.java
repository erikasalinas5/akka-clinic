package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.clinic.domain.CancelAppointmentState;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component(id = "cancel-appointment")
public class CancelAppointmentWorkflow extends Workflow<CancelAppointmentState> {
    private final ComponentClient componentClient;

    public CancelAppointmentWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }
    public record CancelAppointmentCommand(String appointmentId, LocalDateTime dateTime, String doctorId) {}

    public Effect<Done> cancel(CancelAppointmentCommand cmd) {
        System.out.println("Canceling appointment " + cmd.appointmentId);
        if (currentState() != null) {
            return effects().error("Cancel already in progress or finished for this workflow");
        }
        var state = new CancelAppointmentState(cmd.appointmentId, cmd.dateTime, cmd.doctorId, CancelAppointmentState.Status.Initial);
        return effects()
                .updateState(state)
                .transitionTo(CancelAppointmentWorkflow::updateAppointment)
                .thenReply(Done.getInstance());
    }

    public Effect<CancelAppointmentState> getState() {
        return effects().reply(currentState());
    }

    public Effect<Boolean> isCompleted() {
        return effects()
                .reply(currentState().status() == CancelAppointmentState.Status.Failed || currentState().status() == CancelAppointmentState.Status.SlotDeleted);
    }
    public StepEffect updateAppointment() {
        System.out.println("Updating appointment state");
        try{
            componentClient
                    .forEventSourcedEntity(commandContext().workflowId())
                    .method(AppointmentEntity::cancel)
                    .invoke();
            return stepEffects()
                    .updateState(currentState().withStatus(CancelAppointmentState.Status.AppointmentCancelled))
                    .thenTransitionTo(CancelAppointmentWorkflow::deleteTimeSlot);
        }catch(IllegalArgumentException e){
            return stepEffects()
                    .thenEnd();
        }

    }
    public StepEffect deleteTimeSlot() {
        System.out.println("Deleting time slot");
        var scheduleId = new Schedule.ScheduleId(
                currentState().DoctorId(),
                currentState().dateTime().toLocalDate()
                );
        LocalTime startTime = currentState().dateTime().toLocalTime();
        try{
            componentClient
                    .forKeyValueEntity(scheduleId.toString())
                    .method(ScheduleEntity::cancelAppointmentByStartTime)
                    .invoke(startTime);
        } catch (IllegalArgumentException e){
            throw e;
        }
        return  stepEffects()
                .updateState(currentState().withStatus(CancelAppointmentState.Status.SlotDeleted))
                .thenEnd();
    }
    @Override
    public WorkflowSettings settings() {
        return WorkflowSettingsBuilder
                .newBuilder()
                .stepTimeout(CancelAppointmentWorkflow::deleteTimeSlot, Duration.ofNanos(10))
                .defaultStepRecovery(
                        RecoverStrategy.maxRetries(3)
                                .failoverTo(CancelAppointmentWorkflow::deleteTimeSlot)
                )
                .build();
    }
}
