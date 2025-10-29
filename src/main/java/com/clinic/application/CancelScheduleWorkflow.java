package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.WorkflowExceptions;
import akka.javasdk.workflow.Workflow;
import com.clinic.domain.CancelAppointmentState;
import com.clinic.domain.CancelScheduleState;
import com.clinic.domain.Schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Component(id = "cancel-schedule")
public class CancelScheduleWorkflow extends Workflow<CancelScheduleState> {

    private final ComponentClient componentClient;

    public CancelScheduleWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record CancelScheduleCommand(LocalDateTime dateTime, String doctorId){}

    public Effect<Done> cancelSchedule(CancelScheduleCommand cmd) {
        if (currentState() != null){
            return effects().error("Cancel already in progress or finished for this workflow");
        }
        var state = new CancelScheduleState(cmd.dateTime, cmd.doctorId, CancelScheduleState.Status.Initial);
        return effects()
                .updateState(state)
                .transitionTo(CancelScheduleWorkflow::blockSchedule)
                .thenReply(Done.getInstance());
    }

    public Effect<CancelScheduleState> getState(){
        return effects().reply(currentState());
    }

    public Effect<Boolean> isCompleted(){
        return effects()
                .reply(currentState().status() == CancelScheduleState.Status.scheduleCancelled || currentState().status() == CancelScheduleState.Status.Failed);
    }
    public StepEffect blockSchedule(){
        System.out.println("## Block Schedule");
        var scheduleId = new Schedule.ScheduleId(
                currentState().doctorId(),
                currentState().dateTime().toLocalDate()
        );
        try {
            componentClient
                    .forKeyValueEntity(scheduleId.toString())
                    .method(ScheduleEntity::blockDay)
                    .invoke();
            return stepEffects()
                    .updateState(currentState().withStatus(CancelScheduleState.Status.scheduleBlocked))
                    .thenTransitionTo(CancelScheduleWorkflow::rescheduleAppointments);
        } catch (IllegalArgumentException e) {
            return stepEffects().thenEnd();
        }

    }

    public StepEffect rescheduleAppointments() {
        System.out.println("## Reschedule Appointments");
        /*This function should call another workflow that
        1. Order the list of timeslots by priority using an AI AGENT
        2. Check availability in following days
        3. One by one execute reschedulling appointment which command receive appointmentId, newDateTime, doctorId
        and assign the closer timeslot
        * we already have the function to change status of schedule from blocked to cancelled*/
        var scheduleId = new Schedule.ScheduleId(
                currentState().doctorId(),
                currentState().dateTime().toLocalDate()
        );
        Optional<Schedule> schedule = componentClient
                .forKeyValueEntity(scheduleId.toString())
                .method(ScheduleEntity::getSchedule)
                .invoke();
        if (schedule.isEmpty()) {
            return stepEffects()
                    .updateState(currentState().withStatus(CancelScheduleState.Status.Failed))
                    .thenEnd();

        }
        try{
            var scheduleSlot = schedule.get().timeSlots().getFirst();
            LocalDate date = currentState().dateTime().toLocalDate();
            LocalTime startTime = scheduleSlot.startTime();

            LocalDateTime combined = date.atTime(startTime);
            componentClient
                    .forWorkflow(scheduleSlot.appointmentId())
                    .method(CancelAppointmentWorkflow::cancel)
                    .invoke(
                            new CancelAppointmentWorkflow
                                    .CancelAppointmentCommand(
                                    scheduleSlot.appointmentId(),
                                    combined,
                                    currentState().doctorId()));
            return stepEffects()
                    .updateState(currentState().withStatus(CancelScheduleState.Status.scheduleCancelled))
                    .thenTransitionTo(CancelScheduleWorkflow::cancelScheduleStatus);
        } catch (WorkflowExceptions.WorkflowException e) {
            return stepEffects().thenEnd();
        }


    }
    public StepEffect cancelScheduleStatus() {
        System.out.println("## Cancel Schedule Status");
        var scheduleId = new Schedule.ScheduleId(
                currentState().doctorId(),
                currentState().dateTime().toLocalDate()
        );
        try {
            componentClient
                    .forKeyValueEntity(scheduleId.toString())
                    .method(ScheduleEntity::cancelDay)
                    .invoke();
            return stepEffects()
                    .updateState(currentState().withStatus(CancelScheduleState.Status.scheduleCancelled))
                    .thenEnd();
        } catch (IllegalArgumentException e) {
            return stepEffects()
                    .updateState(currentState().withStatus(CancelScheduleState.Status.Failed)).thenEnd();
        }
    }
}
