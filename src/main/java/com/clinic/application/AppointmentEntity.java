package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Query;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.clinic.domain.Appointment;
import com.clinic.domain.AppointmentEvents;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component(id = "appointment")
public class AppointmentEntity extends EventSourcedEntity<Appointment, AppointmentEvents> {

    private String entityId;
    // Adding context using entity ID, then in the events we don't have to send the id
    public AppointmentEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }
    // Create appointment command
    public record CreateAppointmentCmd(LocalDateTime dateTime, String doctorId, String patientId,
                                       String issue) {
    }
    // We expect to return an effect Done from the command
    public Effect<Done> createAppointment(CreateAppointmentCmd cmd) {
        // Only create the appointment if it doesn't exist, avoid create duplicated appointments
        if (currentState() != null)
            return effects().error("Appointment already exists");
        // In effects, we expect to persis the event, if no, return error
        // Here we have a lambda function
        return effects()
                .persist(new AppointmentEvents.AppointmentCreated(commandContext().entityId(), cmd.dateTime(), cmd.doctorId(), cmd.patientId(), cmd.issue()))
                .thenReply(__ -> Done.getInstance());
    }

    public record RescheduleCmd(LocalDateTime newDateTime, String doctorId) {
    }

    public Effect<Done> reschedule(RescheduleCmd cmd) {
        if (currentState() == null)
            return effects().error("Appointment doesn't exist");
        if (currentState().status() != Appointment.Status.PENDING && currentState().status() != Appointment.Status.SCHEDULED)
            return effects().error("Cannot reschedule an appointment that is not pending or scheduled");
        return effects()
                .persist(new AppointmentEvents.Rescheduled(cmd.newDateTime, cmd.doctorId))
                .thenReply(__ -> Done.getInstance());
    }

    public Effect<Done> addNotes(String notes) {
        if (currentState() == null)
            return effects().error("Appointment doesn't exist");
        return effects()
                .persist(new AppointmentEvents.AddedDoctorNotes(notes))
                .thenReply(__ -> Done.getInstance());
    }

    public Effect<Done> addPrescription(String prescription) {
        if (currentState() == null)
            return effects().error("Appointment doesn't exist");
        return effects()
                .persist(new AppointmentEvents.AddedPrescription(prescription))
                .thenReply(__ -> Done.getInstance());
    }
    public Effect<Done> addPriority(String priority) {
        if (currentState() == null)
            return effects().error("Appointment doesn't exist");
        return effects()
                .persist(new AppointmentEvents.AddedPriority(priority))
                .thenReply(__ -> Done.getInstance());
    }

    public Effect<Done> schedule() {
        if (currentState() == null)
            return effects().error("Appointment doesn't exist");
        if (currentState().status() != Appointment.Status.PENDING)
            return effects().error("Cannot schedule an appointment that is not pending");
        return effects()
                .persist(new AppointmentEvents.Scheduled())
                .thenReply(__ -> Done.getInstance());
    }

    public Effect<Done> complete() {
        if (currentState() == null)
            return effects().error("Appointment doesn't exist");
        if (currentState().status() != Appointment.Status.SCHEDULED)
            return effects().error("Cannot complete an appointment that is not scheduled");
        return effects()
                .persist(new AppointmentEvents.Completed())
                .thenReply(__ -> Done.getInstance());
    }

    public Effect<Done> cancel() {
        if (currentState() == null)
            return effects().error("Appointment doesn't exist");
        if (currentState().status() != Appointment.Status.PENDING && currentState().status() != Appointment.Status.SCHEDULED)
            return effects().error("Cannot cancel an appointment that is not pending or scheduled");
        return effects()
                .persist(new AppointmentEvents.Cancelled())
                .thenReply(__ -> Done.getInstance());
    }

    public Effect<Done> markAsMissed() {
        if (currentState() == null)
            return effects().error("Appointment doesn't exist");
        if (currentState().status() != Appointment.Status.PENDING && currentState().status() != Appointment.Status.SCHEDULED)
            return effects().error("Cannot mark an appointment as missed that is not pending or scheduled");
        return effects()
                .persist(new AppointmentEvents.Missed())
                .thenReply(__ -> Done.getInstance());
    }

    public Effect<Optional<Appointment>> getAppointment() {
        return effects().reply(Optional.ofNullable(currentState()));
    }

    // currentState() -> apply event -> return newState()
    @Override
    public Appointment applyEvent(AppointmentEvents event) {
        System.out.println("Applying event: " + event);
        switch (event) {
            case AppointmentEvents.AppointmentCreated e: // currentState() is null
                return new Appointment(entityId, e.dateTime(), e.doctorId(), e.patientId(), e.issue());
            case AppointmentEvents.AddedDoctorNotes e:
                return currentState().addNotes(e.notes());
            case AppointmentEvents.AddedPrescription e:
                return currentState().addPrescription(e.prescription());
            case AppointmentEvents.AddedPriority e:
                return currentState().addPriority(e.priority());
            case AppointmentEvents.Scheduled e:
                return currentState().markAsScheduled();
            case AppointmentEvents.Completed e:
                return currentState().complete();
            case AppointmentEvents.Cancelled e:
                return currentState().cancel();
            case AppointmentEvents.Missed e:
                return currentState().markAsMissed();
            case AppointmentEvents.Rescheduled e:
                return currentState().reschedule(e.dateTime(), e.doctorId());
        }

    }
}