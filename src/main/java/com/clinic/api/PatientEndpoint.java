package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.clinic.application.AppointmentsByPatientView;

import java.util.List;

@HttpEndpoint("patients")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class PatientEndpoint extends AbstractHttpEndpoint {

    private ComponentClient componentClient;

    public PatientEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Get("{patientId}/appointments")
    public List<AppointmentsByPatientView.AppointmentRow> findAppointments(String patientId) {
        return componentClient
                .forView()
                .method(AppointmentsByPatientView::findByPatient)
                .invoke(patientId)
                .appointments();
    }
}