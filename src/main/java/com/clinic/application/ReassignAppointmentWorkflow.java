package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.clinic.domain.ReassignAppointmentState;

import java.time.Duration;
import java.time.LocalDateTime;

@Component(id = "reassign-appointment")
public class ReassignAppointmentWorkflow extends Workflow<ReassignAppointmentState> {
    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);

    private final ComponentClient componentClient;

    public ReassignAppointmentWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record ReassignAppointmentCommand(LocalDateTime dateTime, String doctorId, String patientId, String issue){}

    public Effect<Done> reassignAppointment(ReassignAppointmentCommand command){
        System.out.println("Reassign appointment command");
    }
}
