package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import com.clinic.application.DoctorEntity;
import com.clinic.application.DoctorsView;
import com.clinic.application.SchedulesByDoctorView;
import com.clinic.domain.Doctor;

import java.util.List;
import java.util.Optional;

@HttpEndpoint("doctors")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class DoctorEndpoint extends AbstractHttpEndpoint {

    private ComponentClient componentClient;

    public DoctorEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record CreateDoctorRequest(
            String firstName,
            String lastName,
            List<String> specialities,
            String description,
            Optional<Contact> contact
    ) {
    }

    public record Contact(Optional<String> phone, Optional<String> email) {
    }

    @Post("{id}")
    public void createDoctor(String id, CreateDoctorRequest body) {
        var doctor = new Doctor(id, body.firstName, body.lastName, body.specialities, body.description, body.contact.map(c -> new Doctor.Contact(c.phone, c.email)));
        componentClient.forKeyValueEntity(id)
                .method(DoctorEntity::create)
                .invoke(doctor);
    }

    public record DoctorSummary(String id, String name, List<String> specialities) {
    }

    @Get
    public List<DoctorSummary> getDoctors() {
        Optional<String> optionalSpeciality = requestContext().queryParams().getString("speciality");

        var doctors = optionalSpeciality.map(speciality ->
                componentClient.forView().method(DoctorsView::findBySpeciality).invoke(speciality)
        ).orElseGet(() ->
                componentClient.forView().method(DoctorsView::getDoctors).invoke()
        );

        return doctors.doctors()
                .stream().map(doctor -> new DoctorSummary(doctor.id(), doctor.firstName() + " " + doctor.lastName(), doctor.specialities()))
                .toList();
    }

    public record DoctorDetails(
            String id,
            String firstName,
            String lastName,
            List<String> specialities,
            String description,
            Optional<Contact> contact
    ) {
    }

    @Get("{id}")
    public DoctorDetails getDoctor(String id) {
        var optionalDoctor = componentClient
                .forKeyValueEntity(id)
                .method(DoctorEntity::getDoctor)
                .invoke();
        return optionalDoctor.map(doctor ->
                new DoctorDetails(id, doctor.firstName(), doctor.lastName(), doctor.specialities(), doctor.description(), doctor.contact().map(c -> new Contact(c.phone(), c.email())))
        ).orElseThrow(HttpException::notFound);
    }

    @Get("{doctorId}/schedules")
    public List<SchedulesByDoctorView.ScheduleSummary> getSchedulesByDoctor(String doctorId) {
        return componentClient.forView().method(SchedulesByDoctorView::getSummaries).invoke(new SchedulesByDoctorView.FindScheduleSummary(doctorId, "2025-10-20", "2025-10-30")).schedules();
    }

}