package com.clinic.application;

import akka.javasdk.testkit.TestKitSupport;
import com.clinic.domain.Appointment;
import com.clinic.domain.Schedule;
import com.clinic.domain.RescheduleAppointmentState;
import com.clinic.domain.ScheduleAppointmentState;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.clinic.application.DateUtils.dateTime;
import static com.clinic.application.DateUtils.time;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RescheduleAppointmentWorkflow.
 */
public class RescheduleApptWorkflowIntegrationTest extends TestKitSupport {

    /**
     * Helper: create a working-hours schedule KV entity for a doctor on a given date (yyyy-MM-dd).
     */
    private void createScheduleFor(String doctorId, String date) {
        componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));
    }

    /**
     * Helper: schedule an appointment using the ScheduleAppointmentWorkflow.
     * appointmentId == workflowId of that workflow.
     */
    private void scheduleAppointmentViaWorkflow(
            String appointmentId, String doctorId, String isoDateTime, String patientId, String issue) {

        componentClient
                .forWorkflow(appointmentId)
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(
                        dateTime(isoDateTime), doctorId, patientId, issue));

        // wait until appointment is scheduled
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
    public void rescheduleHappyPath() {
        // Given: schedules for both doctors on the same date and an existing appointment
        createScheduleFor("house", "2031-10-20");
        createScheduleFor("wilson", "2031-10-20");

        // Existing appointment appt-1 with doctor 'house' at 11:00
        scheduleAppointmentViaWorkflow("appt-1", "house", "2031-10-20T11:00:00", "p-1", "cough");

        // When: reschedule appt-1 to doctor 'wilson' at 12:00
        componentClient
                .forWorkflow("res-1")
                .method(RescheduleAppointmentWorkflow::reschedule)
                .invoke(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(
                        "appt-1",
                        dateTime("2031-10-20T12:00:00"),
                        "wilson"
                ));

        // Then: appointment moved to new time/doctor
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Appointment> appt = componentClient
                            .forEventSourcedEntity("appt-1")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    assertTrue(appt.isPresent());
                    assertEquals(Appointment.Status.SCHEDULED, appt.get().status());
                    assertEquals("wilson", appt.get().doctorId());
                    assertEquals(dateTime("2031-10-20T12:00:00"), appt.get().dateTime());
                });

        // Old schedule (house:2031-10-20) should have old slot removed
        var houseSchedule = componentClient
                .forKeyValueEntity("house:2031-10-20")
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertTrue(houseSchedule.isPresent());
        assertEquals(0, houseSchedule.get().timeSlots().size(), "Old slot should be removed");

        // New schedule (wilson:2031-10-20) should have 1 slot
        var wilsonSchedule = componentClient
                .forKeyValueEntity("wilson:2031-10-20")
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertTrue(wilsonSchedule.isPresent());
        assertEquals(1, wilsonSchedule.get().timeSlots().size(), "New slot should be created");

        // Workflow terminal state
        var wfState = componentClient
                .forWorkflow("res-1")
                .method(RescheduleAppointmentWorkflow::getState)
                .invoke();
        assertEquals(RescheduleAppointmentState.Status.OldSlotRemoved, wfState.status());
    }

    @Test
    public void rescheduleMissingAppointment() {
        // Given: no appointment with id 'missing-appt'
        createScheduleFor("house", "2031-10-21"); // schedule existing is irrelevant, appt is missing

        // When: try to reschedule a non-existent appointment
        componentClient
                .forWorkflow("res-missing")
                .method(RescheduleAppointmentWorkflow::reschedule)
                .invoke(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(
                        "missing-appt",
                        dateTime("2031-10-21T11:30:00"),
                        "house"
                ));

        // Then: workflow should fail and end (no exception thrown by design)
        var wfState = componentClient
                .forWorkflow("res-missing")
                .method(RescheduleAppointmentWorkflow::getState)
                .invoke();
        assertEquals(RescheduleAppointmentState.Status.Failed, wfState.status());

        // Also verify appointment truly doesn't exist
        Optional<Appointment> appt = componentClient
                .forEventSourcedEntity("missing-appt")
                .method(AppointmentEntity::getAppointment)
                .invoke();
        assertTrue(appt.isEmpty());
    }

    @Test
    public void rescheduleNewSlotUnavailable() {
        // Given:
        createScheduleFor("house", "2031-10-22");
        createScheduleFor("wilson", "2031-10-22");

        // Existing bookings: appt-2 (house@11:00) and busy-wilson (wilson@12:00)
        scheduleAppointmentViaWorkflow("appt-2", "house", "2031-10-22T11:00:00", "p-2", "flu");
        scheduleAppointmentViaWorkflow("busy-wilson", "wilson", "2031-10-22T12:00:00", "p-x", "occupied");

        // When: try to move appt-2 to wilson@12:00 (already taken)
        componentClient
                .forWorkflow("res-2")
                .method(RescheduleAppointmentWorkflow::reschedule)
                .invoke(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(
                        "appt-2",
                        dateTime("2031-10-22T12:00:00"),
                        "wilson"
                ));

        // Then: wait for the workflow to process and fail
        Awaitility.await()
                .atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var wfState = componentClient
                            .forWorkflow("res-2")
                            .method(RescheduleAppointmentWorkflow::getState)
                            .invoke();
                    assertEquals(RescheduleAppointmentState.Status.Failed, wfState.status());
                });

        // Appointment remains unchanged
        Optional<Appointment> appt2 = componentClient
                .forEventSourcedEntity("appt-2")
                .method(AppointmentEntity::getAppointment)
                .invoke();

        assertTrue(appt2.isPresent());
        assertEquals(Appointment.Status.SCHEDULED, appt2.get().status());
        assertEquals("house", appt2.get().doctorId());
        assertEquals(dateTime("2031-10-22T11:00:00"), appt2.get().dateTime());

        // Schedules unchanged: house still has appt-2, wilson still has the earlier booking
        var houseSchedule = componentClient
                .forKeyValueEntity("house:2031-10-22")
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertTrue(houseSchedule.isPresent());
        assertEquals(1, houseSchedule.get().timeSlots().size());

        var wilsonSchedule = componentClient
                .forKeyValueEntity("wilson:2031-10-22")
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertTrue(wilsonSchedule.isPresent());
        assertEquals(1, wilsonSchedule.get().timeSlots().size());
    }


    @Test
    public void rescheduleDoubleStart() {
        // Given: existing appointment to reschedule
        createScheduleFor("house", "2031-10-23");
        createScheduleFor("wilson", "2031-10-23");

        scheduleAppointmentViaWorkflow("appt-3", "house", "2031-10-23T11:00:00", "p-3", "ache");

        // Start reschedule once
        componentClient
                .forWorkflow("res-dup")
                .method(RescheduleAppointmentWorkflow::reschedule)
                .invoke(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(
                        "appt-3",
                        dateTime("2031-10-23T12:00:00"),
                        "wilson"
                ));

        // Immediately try to start the same workflow again → should error
        RuntimeException error = assertThrows(RuntimeException.class, () ->
                componentClient
                        .forWorkflow("res-dup")
                        .method(RescheduleAppointmentWorkflow::reschedule)
                        .invoke(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(
                                "appt-3",
                                dateTime("2031-10-23T12:30:00"),
                                "wilson"
                        ))
        );
        assertTrue(error.getMessage().contains("Reschedule already in progress or finished for this workflow"));
    }
}
