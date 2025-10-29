package com.clinic.application;

import akka.javasdk.testkit.TestKitSupport;
import com.clinic.domain.Appointment;
import com.clinic.domain.Schedule;
import com.clinic.domain.ScheduleAppointmentState;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import javax.swing.text.html.Option;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.clinic.application.DateUtils.dateTime;
import static com.clinic.application.DateUtils.time;
import static org.junit.jupiter.api.Assertions.*;

public class ScheduleAppointmentWorkflowIntegrationTest extends TestKitSupport {

    @Test
    public void scheduleAppointment() {
        componentClient
                .forKeyValueEntity("house:2031-10-20")
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));

        componentClient
                .forWorkflow("1")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(dateTime("2031-10-20T11:00:00"), "house", "2", "issue"));

        Awaitility.await()
                .atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    System.out.println("Waiting for appointment to be created");
                    Optional<Appointment> appointment = componentClient
                            .forEventSourcedEntity("1")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    assertTrue(appointment.isPresent());
                    assertEquals(Appointment.Status.SCHEDULED, appointment.get().status());
                });

        var updatedSchedule = componentClient.forKeyValueEntity("house:2031-10-20")
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertEquals(1, updatedSchedule.get().timeSlots().size());

        var workflowState = componentClient.forWorkflow("1").method(ScheduleAppointmentWorkflow::getState).invoke();

        assertEquals(ScheduleAppointmentState.Status.AppointmentScheduled, workflowState.status());
    }

    @Test
    public void scheduleTwice() {
        componentClient
                .forKeyValueEntity("house:2031-10-21")
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));

        // First schedule (works fine)
        var response1 = componentClient
                .forWorkflow("10")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(
                        dateTime("2031-10-21T11:00:00"),
                        "house",
                        "5",
                        "first"
                ));
        assertEquals("Done", response1.toString());

        // Second schedule with same workflow ID → must fail
        var error = assertThrows(RuntimeException.class, () ->
                componentClient
                        .forWorkflow("10")
                        .method(ScheduleAppointmentWorkflow::schedule)
                        .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(
                                dateTime("2031-10-21T12:00:00"),
                                "house",
                                "5",
                                "duplicate"
                        ))
        );
        assertTrue(error.getMessage().contains("Appointment already exists"));

    }

    @Test
    public void scheduleOverlapping() {
        componentClient
                .forKeyValueEntity("house:2031-10-22")
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));

        // First appointment succeeds
        componentClient
                .forWorkflow("20")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(
                        dateTime("2031-10-22T11:00:00"),
                        "house",
                        "7",
                        "first"
                ));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var schedule = componentClient
                            .forKeyValueEntity("house:2031-10-22")
                            .method(ScheduleEntity::getSchedule)
                            .invoke();
                    assertEquals(1, schedule.get().timeSlots().size());
                });

        // Second overlapping appointment (same time)
        componentClient
                .forWorkflow("21")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(
                        dateTime("2031-10-22T11:00:00"),
                        "house",
                        "8",
                        "overlap"
                ));

        // Wait to ensure workflow ends, but appointment should NOT be scheduled
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var appt = componentClient
                            .forEventSourcedEntity("21")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    // appointment entity exists but should have been cancelled
                    assertTrue(appt.isPresent());
                    assertEquals(Appointment.Status.CANCELLED, appt.get().status());
                });
    }

    @Test
    public void scheduleDoesntExist() {
        // Don't create a schedule entity for this date
        componentClient
                .forWorkflow("30")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(
                        dateTime("2031-10-23T11:00:00"),
                        "ghostDoctor",
                        "9",
                        "no schedule"
                ));
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                        .untilAsserted(() -> {
                            Optional<Appointment> appointment = componentClient
                                    .forEventSourcedEntity("30")
                                    .method(AppointmentEntity::getAppointment)
                                    .invoke();
                            assertTrue(appointment.isPresent());
                            assertEquals(Appointment.Status.CANCELLED, appointment.get().status());
                        });
        var workflowState = componentClient
                .forWorkflow("30")
                        .method(ScheduleAppointmentWorkflow::getState)
                                .invoke();
        assertEquals(ScheduleAppointmentState.Status.AppointmentCancelled, workflowState.status());

    }


}