package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.clinic.domain.Doctor;

import java.util.Optional;

@Component(id = "doctor")
public class DoctorEntity extends KeyValueEntity<Doctor> {

    public Effect<Done> create(Doctor doctor) {
        if (currentState() != null)
            effects().error("Doctor already exists");
        return effects().updateState(doctor).thenReply(Done.getInstance());
    }

    public Effect<Done> update(Doctor doctor) {
        if (currentState() == null)
            effects().error("Doctor doesn't exist");
        return effects().updateState(doctor).thenReply(Done.getInstance());
    }

    public Effect<Optional<Doctor>> getDoctor() {
        return effects().reply(Optional.ofNullable(currentState()));
    }
}