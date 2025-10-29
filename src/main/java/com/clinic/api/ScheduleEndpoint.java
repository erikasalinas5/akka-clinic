package com.clinic.api;

import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import com.clinic.application.*;
import com.clinic.domain.Schedule;
import com.clinic.application.AppointmentsByPatientView;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.clinic.api.common.Validation.parseDate;
import static com.clinic.api.common.Validation.parseTime;

@HttpEndpoint("schedules")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ScheduleEndpoint extends AbstractHttpEndpoint {

    private ComponentClient componentClient;

    public ScheduleEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public static final String DOCTOR_ID_HEADER = "doctorId";

    public record WorkingHours(String startTime, String endTime) {
    }

    public record CreateScheduleRequest(
            WorkingHours workingHours
    ) {
    }

    @Put("{day}")
    public void upsertSchedule(String day, CreateScheduleRequest body) {
        var doctorId = requestContext()
                .requestHeader(DOCTOR_ID_HEADER)
                .map(HttpHeader::value)
                .orElseThrow(() -> HttpException.badRequest("Missing doctorId header"));
        LocalDate date = parseDate(day);
        if (date.isBefore(LocalDate.now())) {
            throw HttpException.badRequest("Cannot schedule for past dates");
        }
        var scheduleId = new Schedule.ScheduleId(doctorId, date);
        var workingHours = new Schedule.WorkingHours(
                parseTime(body.workingHours.startTime),
                parseTime(body.workingHours().endTime)
        );

        componentClient
                .forKeyValueEntity(scheduleId.toString())
                .method(ScheduleEntity::createSchedule)
                .invoke(workingHours);
    }

    @Put("{day}/{doctorId}/cancel")
    public void cancelSchedule(String day, String doctorId){
        LocalDate date = parseDate(day);
        LocalDateTime dateTime = date.atStartOfDay();
        if (date.isBefore(LocalDate.now())) {
            throw HttpException.badRequest("Cannot cancel schedule for past dates");
        }
        var scheduleId = new Schedule.ScheduleId(doctorId, date);
        componentClient
                .forWorkflow(scheduleId.toString())
                .method(CancelScheduleWorkflow::cancelSchedule)
                .invoke(new CancelScheduleWorkflow.CancelScheduleCommand(dateTime, doctorId));
    }

    @Get("by-speciality/{speciality}")
    public List<SchedulesByDoctorView.ScheduleRow> getSchedulesBySpeciality(String speciality) {
        // 1) Get doctors for the speciality
        var doctors = componentClient
                .forView() // DoctorsView @Component(id="doctors")
                .method(DoctorsView::findBySpeciality)
                .invoke(speciality)
                .doctors();

        // 2) For each doctor, fetch schedules and flatten
        return doctors.stream()
                .flatMap(doc ->
                        componentClient
                                .forView() // SchedulesByDoctorView @Component(id="schedules-by-doctor")
                                .method(SchedulesByDoctorView::getSchedules)
                                .invoke(doc.id())
                                .schedules()
                                .stream()
                )
                .toList();
    }

    /**
     * GET /schedules/by-speciality/{speciality}/summaries?from=YYYY-MM-DD&to=YYYY-MM-DD
     * Returns (doctorId, date) summaries for all doctors with the speciality within the date range.
     * NOTE: The underlying view uses exclusive bounds (> from, < to).
     */
    @Get("by-speciality/{speciality}/summaries")
    public List<SchedulesByDoctorView.ScheduleSummary> getSchedulesBySpecialitySummaries(String speciality) {
        var qp = requestContext().queryParams();
        var fromStr = qp.getString("from")
                .orElseThrow(() -> HttpException.badRequest("Missing 'from' query param (YYYY-MM-DD)"));
        var toStr = qp.getString("to")
                .orElseThrow(() -> HttpException.badRequest("Missing 'to' query param (YYYY-MM-DD)"));

        var from = parseDate(fromStr);
        var to = parseDate(toStr);
        if (!from.isBefore(to)) {
            throw HttpException.badRequest("'from' must be before 'to'");
        }

        // 1) Get doctors for the speciality
        var doctors = componentClient
                .forView()
                .method(DoctorsView::findBySpeciality)
                .invoke(speciality)
                .doctors();

        // 2) Query summaries for each doctor and flatten
        return doctors.stream()
                .flatMap(doc -> {
                    var query = new SchedulesByDoctorView.FindScheduleSummary(
                            doc.id(), from.toString(), to.toString()
                    );
                    return componentClient
                            .forView()
                            .method(SchedulesByDoctorView::getSummaries)
                            .invoke(query)
                            .schedules()
                            .stream();
                })
                .toList();
    }

}
