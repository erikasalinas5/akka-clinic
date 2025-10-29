package com.clinic.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.clinic.domain.Appointment;
import com.clinic.domain.AppointmentEvents;

import java.util.List;

@Component(id = "appointments-by-patient")
public class AppointmentsByPatientView extends View {
    public record AppointmentRow(String id, String patientId, String doctorId, String issue, String date, String time,
                                 Appointment.Status status) {
        public AppointmentRow withStatus(Appointment.Status status) {
            return new AppointmentRow(id, patientId, doctorId, issue, date, time, status);
        }

        public AppointmentRow withDate(String date, String time) {
            return new AppointmentRow(id, patientId, doctorId, issue, date, time, status);
        }

        public AppointmentRow withDoctorId(String doctorId) {
            return new AppointmentRow(id, patientId, doctorId, issue, date, time, status);
        }
    }

    @Consume.FromEventSourcedEntity(AppointmentEntity.class)
    public static class Updater extends TableUpdater<AppointmentRow> {
        public Effect<AppointmentRow> onEvent(AppointmentEvents event) {
            return switch (event) {
                case AppointmentEvents.AppointmentCreated e -> {
                    var row = new AppointmentRow(e.id(),e.patientId(), e.doctorId(), e.issue(), e.dateTime().toLocalDate().toString(), e.dateTime().toLocalTime().toString(), Appointment.Status.PENDING);
                    yield effects().updateRow(row);
                }
                case AppointmentEvents.AddedDoctorNotes e -> effects().ignore();
                case AppointmentEvents.AddedPrescription e -> effects().ignore();
                case AppointmentEvents.AddedPriority e -> effects().ignore();
                case AppointmentEvents.Scheduled e -> {
                    var newRow = rowState().withStatus(Appointment.Status.SCHEDULED);
                    yield effects().updateRow(newRow);
                }
                case AppointmentEvents.Completed e -> {
                    var newRow = rowState().withStatus(Appointment.Status.COMPLETED);
                    yield effects().updateRow(newRow);
                }
                case AppointmentEvents.Cancelled e -> {
                    var newRow = rowState().withStatus(Appointment.Status.CANCELLED);
                    yield effects().updateRow(newRow);
                }
                case AppointmentEvents.Missed e -> {
                    var newRow = rowState().withStatus(Appointment.Status.MISSED);
                    yield effects().updateRow(newRow);
                }
                case AppointmentEvents.Rescheduled e -> {
                    var newRow = rowState()
                            .withDate(e.dateTime().toLocalDate().toString(), e.dateTime().toLocalTime().toString())
                            .withDoctorId(e.doctorId());
                    yield effects().updateRow(newRow);
                }
            };
        }
    }

    public record AppointmentRows(List<AppointmentRow> appointments){}

    @Query("SELECT * AS appointments FROM appointments WHERE patientId = :patientId")
    public QueryEffect<AppointmentRows> findByPatient(String patientId) {
        return queryResult();
    }

    public record FindApptDoctorDate(String doctorId, String date){}

    @Query("SELECT * AS appointments FROM appointments WHERE doctorId = :doctorId AND date = :date")
    public QueryEffect<AppointmentRows> findByDoctorAndDate(FindApptDoctorDate query) {
        return queryResult();
    }

}