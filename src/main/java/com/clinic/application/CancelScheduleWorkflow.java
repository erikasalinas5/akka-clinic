package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.WorkflowExceptions;
import akka.javasdk.workflow.Workflow;
import com.clinic.application.ai.PriorityAgent;
import com.clinic.domain.CancelScheduleState;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
                    .thenTransitionTo(CancelScheduleWorkflow::addPriorityAppointments);
        } catch (IllegalArgumentException e) {
            return stepEffects().thenEnd();
        }

    }

    public StepEffect addPriorityAppointments(){
        System.out.println("## List appointments");
        try {
            AppointmentsByPatientView.AppointmentRows appointmentsDay = componentClient
                    .forView()
                    .method(AppointmentsByPatientView::findByDoctorAndDate)
                    .invoke(new AppointmentsByPatientView.FindApptDoctorDate(currentState().doctorId(), currentState().dateTime().toLocalDate().toString()));
            if (!appointmentsDay.appointments().isEmpty()) {
                var futures = appointmentsDay.appointments().stream()
                        .map(appointment ->
                                componentClient.forAgent()
                                        .inSession(appointment.id())
                                        .method(PriorityAgent::urgency)
                                        .invokeAsync(appointment.issue())
                                        .thenCompose(priority ->
                                                componentClient.forEventSourcedEntity(appointment.id())
                                                        .method(AppointmentEntity::addPriority)
                                                        .invokeAsync(priority)
                                        )
                        )
                        .toList();
                CompletableFuture<Void> all = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                );
                all.join();
                System.out.println(all);

                return stepEffects()
                        .thenTransitionTo(CancelScheduleWorkflow::orderAppointments);
            }
            return stepEffects()
                    .thenTransitionTo(CancelScheduleWorkflow::cancelScheduleStatus);
        }
        catch (IllegalArgumentException e) {
            return stepEffects()
                    .thenTransitionTo(CancelScheduleWorkflow::cancelScheduleStatus);
        }
    }

    private int getPriorityOrder(String priority) {
        return switch (priority.toLowerCase()) {
            case "high" -> 1;
            case "medium" -> 2;
            case "low" -> 3;
            default -> 99; // Default or unassigned priority goes last
        };
    }
    private static String safeLower(Optional<String> s) { return s == null ? "" : s.toString().toLowerCase(); }


    public StepEffect orderAppointments() {
        System.out.println("## Reschedule Appointments");
        AppointmentsByPatientView.AppointmentRows appointmentsDay = componentClient
                .forView()
                .method(AppointmentsByPatientView::findByDoctorAndDate)
                .invoke(new AppointmentsByPatientView.FindApptDoctorDate(currentState().doctorId(), currentState().dateTime().toLocalDate().toString()));

        if (appointmentsDay == null || appointmentsDay.appointments().isEmpty()) {
            System.out.println("No appointments to order.");
            return stepEffects()
                    .updateState(currentState().withStatus(CancelScheduleState.Status.Failed))
                    .thenEnd();
        }
        List<AppointmentsByPatientView.AppointmentRow> appointments =
                new java.util.ArrayList<>(appointmentsDay.appointments());
        appointments.sort(
                Comparator
                        .comparing((AppointmentsByPatientView.AppointmentRow a) ->
                                getPriorityOrder(safeLower(a.priority())))
                        .thenComparing(AppointmentsByPatientView.AppointmentRow::time)
        );


        try {
            // Build a sequential async chain: each step starts after the previous completes
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            AtomicInteger seq = new AtomicInteger(1);

            for (var appt : appointments) {
                int order = seq.getAndIncrement();

                    componentClient
                            .forWorkflow(appt.id())  // <- target workflow per appointment
                            .method(CancelAppointmentWorkflow::cancel) // your method
                            .invokeAsync(new CancelAppointmentWorkflow.CancelAppointmentCommand(
                                    appt.id(),
                                    LocalDateTime.of(currentState().dateTime().toLocalDate(), LocalTime.parse(appt.time())),
                                    appt.doctorId()
                ));
            }

            // wait for the whole chain (you can add a timeout)
            chain.orTimeout(60, java.util.concurrent.TimeUnit.SECONDS).join();

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

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettingsBuilder
                .newBuilder()
                .stepTimeout(CancelScheduleWorkflow::addPriorityAppointments, Duration.ofSeconds(40))
                .defaultStepRecovery(RecoverStrategy.maxRetries(2).failoverTo(CancelScheduleWorkflow::cancelScheduleStatus))
                .build();
    }
}
