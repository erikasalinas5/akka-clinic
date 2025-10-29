package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.clinic.domain.Doctor;

import java.util.List;
import java.util.Optional;

@Component(id = "doctors")
public class DoctorsView extends View {

    @Consume.FromKeyValueEntity(DoctorEntity.class)
    public static class Updator extends TableUpdater<Doctor> {}

    public record Doctors(List<Doctor> doctors) {}

    @Query("SELECT * AS doctors FROM doctors")
    public QueryEffect<Doctors> getDoctors() {
        return queryResult();
    }

    @Query("SELECT * AS doctors FROM doctors WHERE :speciality = ANY(specialities)")
    public QueryEffect<Doctors> findBySpeciality(String speciality) {
        return queryResult();
    }
}