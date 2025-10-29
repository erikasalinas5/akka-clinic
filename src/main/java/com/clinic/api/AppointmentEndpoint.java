package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import com.clinic.application.*;
import com.clinic.domain.Appointment;
import com.clinic.application.AppointmentsByPatientView;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.clinic.api.common.Validation.parseDate;
import static com.clinic.api.common.Validation.parseTime;

@HttpEndpoint("appointments")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class AppointmentEndpoint extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;

    public AppointmentEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record CreateAppointmentRequest(String doctorId, String date, String startTime, String issue, String patientId) {
    }

    public record CreateAppointmentResponse(String id) {
    }


    @Post
    public CreateAppointmentResponse scheduleAppointment(CreateAppointmentRequest body) {
        LocalDate date = parseDate(body.date);
        if (date.isBefore(LocalDate.now())) {
            throw HttpException.badRequest("Cannot schedule an appointment for past dates");
        }
        var appointmentId = UUID.randomUUID().toString();
        try {
            componentClient
                    .forWorkflow(appointmentId) // workflowId stays as the appointment id in your design
                    .method(ScheduleAppointmentWorkflow::schedule)
                    .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(
                            date.atTime(parseTime(body.startTime)),
                            body.doctorId,
                            body.patientId,
                            body.issue
                    ));
        } catch (IllegalArgumentException e) {
            // Errors thrown via effects().error(...) or entity errors land here
            throw HttpException.badRequest(e.getMessage());
        }

        return new CreateAppointmentResponse(appointmentId);
    }

    public record RescheduleAppointmentRequest(String doctorId, String date, String startTime) {
    }
    public record RescheduleAppointmentResponse(String id) {}

    @Put("{id}")
    public RescheduleAppointmentResponse reschedule(String id, RescheduleAppointmentRequest body) {
        var date = parseDate(body.date);
        var time = parseTime(body.startTime);
        var newDateTime = date.atTime(time);

        if (date.isBefore(LocalDate.now())) {
            throw HttpException.badRequest("Cannot reschedule an appointment for past dates");
        }

        // Optional: disallow rescheduling to a past time on same day
        if (newDateTime.isBefore(LocalDateTime.now())) {
            throw HttpException.badRequest("Cannot reschedule an appointment to a past time");
        }

        // Generate unique workflow ID (different from appointment ID)
        var workflowId = UUID.randomUUID().toString();

        // Call the workflow, passing appointmentId explicitly
        componentClient
                .forWorkflow(workflowId)
                .method(RescheduleAppointmentWorkflow::reschedule)
                .invoke(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(
                        id,
                        newDateTime,
                        body.doctorId
                ));

        return new RescheduleAppointmentResponse(workflowId);
    }

    public record AddNotesRequest(String notes) {
    }
    @Put("{id}/notes")
    public void addNotes(String id, AddNotesRequest body) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::addNotes)
                .invoke(body.notes());
    }


    public record AddPrescriptionRequest(String prescription) {
    }

    @Post("{id}/prescriptions")
    public void addPrescription(String id, AddPrescriptionRequest body) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::addPrescription)
                .invoke(body.prescription());
    }

    @Put("{id}/complete")
    public void complete(String id) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::complete)
                .invoke();
    }

    @Put("{id}/cancel")
    public void cancel(String id) {
        Optional<Appointment> maybeAppt =
                componentClient
                        .forEventSourcedEntity(id)
                        .method(AppointmentEntity::getAppointment)
                        .invoke();

        if (maybeAppt.isEmpty()) {
            throw new IllegalArgumentException("Cannot cancel an appointment without an appointment");

        }
        var appt = maybeAppt.get();
        if (appt.status() == Appointment.Status.CANCELLED) {
            throw new IllegalArgumentException("Appointment '" + id + "' is already cancelled");
        }
        LocalDateTime dateTime = maybeAppt.get().dateTime();
        String doctorId = maybeAppt.get().doctorId();
        componentClient
                .forWorkflow(id)
                .method(CancelAppointmentWorkflow::cancel)
                .invoke(new CancelAppointmentWorkflow.CancelAppointmentCommand(
                        id,
                        dateTime,
                        doctorId
                ));
    }

    @Put("{id}/missed")
    public void missed(String id) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::markAsMissed)
                .invoke();
    }

    @Get("{id}")
    public Appointment getAppointment(String id) {
        return componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::getAppointment)
                .invoke()
                .orElseThrow(HttpException::notFound);
    }
    @Get("{doctorId}/{date}")
    public List<AppointmentsByPatientView.AppointmentRow> findByDoctorAndDate(String doctorId, String date) {
        var query = new AppointmentsByPatientView.FindApptDoctorDate(doctorId, date);
        return componentClient
                .forView()
                .method(AppointmentsByPatientView::findByDoctorAndDate)
                .invoke(query)
                .appointments();
    }
}