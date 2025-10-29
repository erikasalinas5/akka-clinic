package com.clinic.application;

import akka.javasdk.testkit.TestKitSupport;
import com.clinic.domain.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static com.clinic.application.DateUtils.time;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ScheduleEntityIntegrationTest extends TestKitSupport {

    @Test
    public void checkOverlapping() {
        createSchedule("house", "2031-10-20", "10:00", "16:00");
        scheduleAppointment("house", "2031-10-20", "11:00", "a1");
        assertEquals(1, getSchedule("house", "2031-10-20").get().timeSlots().size());

        assertThrows(IllegalArgumentException.class, () ->
                scheduleAppointment("house", "2031-10-20", "11:10", "a2")
        );
        assertEquals(1, getSchedule("house", "2031-10-20").get().timeSlots().size());
    }

    private void createSchedule(String doctorId, String date, String startTime, String endTime) {
        componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time(startTime), time(endTime)));
    }

    private Optional<Schedule> getSchedule(String doctorId, String date) {
        return componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::getSchedule)
                .invoke();
    }

    private void scheduleAppointment(String doctorId, String date, String startTime, String appointmentId) {
        componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::scheduleAppointment)
                .invoke(new ScheduleEntity.ScheduleAppointmentData(time(startTime), Duration.ofMinutes(30), appointmentId));
    }
}