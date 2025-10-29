package com.clinic.application;

import akka.javasdk.testkit.TestKitSupport;
import com.clinic.domain.Appointment;
import com.clinic.domain.CancelAppointmentState;
import com.clinic.domain.Schedule;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.clinic.application.DateUtils.dateTime;
import static com.clinic.application.DateUtils.time;
import static org.junit.jupiter.api.Assertions.*;

public class CancelApptWorkflowIntegrationTest extends TestKitSupport {
    private void createSchedule(String doctorId, String ymd, String start, String end) {
        componentClient
                .forKeyValueEntity(doctorId + ":" + ymd)
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time(start), time(end)));
    }
    private void scheduleAppointmentViaWorkflow(
            String appointmentId, String doctorId, String isoDateTime, String patientId, String issue) {

        componentClient
                .forWorkflow(appointmentId)
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(
                        dateTime(isoDateTime), doctorId, patientId, issue));

        // wait until the appointment entity is scheduled
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Appointment> appt = componentClient
                            .forEventSourcedEntity(appointmentId)
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    assertTrue(appt.isPresent());
                    assertEquals(Appointment.Status.SCHEDULED, appt.get().status());
                });
    }

    @Test
    public void cancelHappyPath_removesSlot_andEnds() {
        // Given a working schedule and an existing scheduled appointment
        createSchedule("house", "2031-10-20", "10:00", "16:00");
        scheduleAppointmentViaWorkflow("appt-100", "house", "2031-10-20T11:00:00", "p-1", "checkup");

        // When: start the cancel workflow using the appointment id as workflow id
        componentClient
                .forWorkflow("appt-100")
                .method(CancelAppointmentWorkflow::cancel)
                .invoke(new CancelAppointmentWorkflow.CancelAppointmentCommand(
                        "appt-100",
                        dateTime("2031-10-20T11:00:00"),
                        "house"
                ));

        // Then: the appointment becomes CANCELLED
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var appt = componentClient
                            .forEventSourcedEntity("appt-100")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    assertTrue(appt.isPresent());
                    assertEquals(Appointment.Status.CANCELLED, appt.get().status());
                });

        // And the time slot is removed from the doctor's schedule
        var schedule = componentClient
                .forKeyValueEntity("house:2031-10-20")
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertTrue(schedule.isPresent());
        assertEquals(0, schedule.get().timeSlots().size());

        // And the workflow state is terminal (SlotDeleted) and isCompleted = true
        var wfState = componentClient
                .forWorkflow("appt-100")
                .method(CancelAppointmentWorkflow::getState)
                .invoke();
        assertEquals(CancelAppointmentState.Status.SlotDeleted, wfState.status());

        var completed = componentClient
                .forWorkflow("appt-100")
                .method(CancelAppointmentWorkflow::isCompleted)
                .invoke();
        assertTrue(completed);
    }

    @Test
    public void cancelTwice_sameWorkflowId_rejected() {
        // Given a scheduled appointment
        createSchedule("house", "2031-10-21", "10:00", "16:00");
        scheduleAppointmentViaWorkflow("appt-200", "house", "2031-10-21T11:00:00", "p-2", "issue");

        // First cancel starts fine
        componentClient
                .forWorkflow("appt-200")
                .method(CancelAppointmentWorkflow::cancel)
                .invoke(new CancelAppointmentWorkflow.CancelAppointmentCommand(
                        "appt-200",
                        dateTime("2031-10-21T11:00:00"),
                        "house"
                ));

        // Second cancel with the SAME workflow id should error
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                componentClient
                        .forWorkflow("appt-200")
                        .method(CancelAppointmentWorkflow::cancel)
                        .invoke(new CancelAppointmentWorkflow.CancelAppointmentCommand(
                                "appt-200",
                                dateTime("2031-10-21T11:00:00"),
                                "house"
                        ))
        );
        assertTrue(ex.getMessage().contains("Cancel already in progress or finished for this workflow"));
    }

    @Test
    public void cancelNonexistentAppointment_leavesInitial_andNotCompleted() {
        // Given no appointment exists with this id
        String missingId = "no-such-appt";

        // When: start the cancel workflow
        componentClient
                .forWorkflow(missingId)
                .method(CancelAppointmentWorkflow::cancel)
                .invoke(new CancelAppointmentWorkflow.CancelAppointmentCommand(
                        missingId,
                        dateTime("2031-10-22T11:00:00"),
                        "house"
                ));

        // Then: updateAppointment catches the error and ends; state remains Initial (per workflow code)
        var wfState = componentClient
                .forWorkflow(missingId)
                .method(CancelAppointmentWorkflow::getState)
                .invoke();
        assertEquals(CancelAppointmentState.Status.Initial, wfState.status());

        // And isCompleted = false (only Failed or SlotDeleted are terminal in isCompleted())
        var completed = componentClient
                .forWorkflow(missingId)
                .method(CancelAppointmentWorkflow::isCompleted)
                .invoke();
        assertFalse(completed);
    }

}
