package com.clinic.domain;


import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

public class ScheduleTest {

    Schedule.ScheduleId houseScheduleId = new Schedule.ScheduleId("house", LocalDate.of(2021, 1, 1));
    Schedule.WorkingHours defaultWorkingHours = new Schedule.WorkingHours(LocalTime.of(10, 0), LocalTime.of(18, 0));

    @Nested
    public class TimeSlotTest {

        @Test
        public void createValidAppointment() {
            var actual = appointment("10:00", "11:00");

            assertEquals(LocalTime.of(10, 0), actual.startTime());
            assertEquals(LocalTime.of(11, 0), actual.endTime());
        }

        @Test
        public void endTimeIsBeforeStartTime() {
            assertThrows(IllegalArgumentException.class, () -> appointment("12:00", "11:00"));
        }

        @Test
        public void endTimeIsSameAsStartTime() {
            assertThrows(IllegalArgumentException.class, () -> appointment("12:00", "12:00"));
        }

        @Test
        public void appointmentIsTooShort() {
            assertThrows(IllegalArgumentException.class, () -> appointment("12:00", "12:01"));
            assertThrows(IllegalArgumentException.class, () -> appointment("12:00", "12:03"));
            assertThrows(IllegalArgumentException.class, () -> appointment("12:00", "12:04"));
            assertDoesNotThrow(() -> appointment("12:00", "12:05"));
        }

        @Test
        public void overlappingtimeSlots() {
            var slot = appointment("10:00", "11:00");

            assertTrue(slot.overlaps(appointment("10:00", "11:00")));
            assertTrue(slot.overlaps(appointment("10:50", "11:10")));
            assertTrue(slot.overlaps(appointment("09:50", "10:10")));
            assertTrue(slot.overlaps(appointment("09:50", "11:10")));
            assertTrue(slot.overlaps(appointment("10:10", "10:50")));

            assertFalse(slot.overlaps(appointment("09:10", "09:50")));
            assertFalse(slot.overlaps(appointment("11:10", "11:50")));
            assertFalse(slot.overlaps(appointment("09:10", "10:00")));
            assertFalse(slot.overlaps(appointment("11:00", "11:50")));
        }
    }

    @Nested
    public class WorkingHoursTest {
        @Test
        public void isInsideWorkingHours() {
            assertTrue(defaultWorkingHours.isInWorkingHours(appointment("10:00", "18:00")));
            assertTrue(defaultWorkingHours.isInWorkingHours(appointment("10:00", "11:00")));
            assertTrue(defaultWorkingHours.isInWorkingHours(appointment("17:00", "18:00")));
            assertFalse(defaultWorkingHours.isInWorkingHours(appointment("09:00", "10:00")));
            assertFalse(defaultWorkingHours.isInWorkingHours(appointment("18:00", "19:00")));
        }
    }

    @Test
    public void addAppointment() {
        var schedule = new Schedule(houseScheduleId, defaultWorkingHours);

        var newSchedule = schedule.scheduleAppointment(LocalTime.of(10, 30), Duration.ofMinutes(30), "a1");
        assertEquals(1, newSchedule.timeSlots().size());
        assertEquals(0, schedule.timeSlots().size());
    }

    @Test
    public void addOverlappingAppointment() {
        var schedule = new Schedule(houseScheduleId, defaultWorkingHours); //an empty schedule
        var scheduleNew1 = schedule.scheduleAppointment(LocalTime.of(10, 30), Duration.ofMinutes(30), "a1");
        assertEquals(1, scheduleNew1.timeSlots().size());

        assertThrows(IllegalArgumentException.class, () ->
                scheduleNew1.scheduleAppointment(LocalTime.of(10, 30), Duration.ofMinutes(30), "a1")
        );
        assertThrows(IllegalArgumentException.class, () ->
                scheduleNew1.scheduleAppointment(LocalTime.of(10, 15), Duration.ofMinutes(30), "a1")
        );

        assertEquals(1, scheduleNew1.timeSlots().size());
    }

    @Test
    public void busySlotsShouldBeInsideWorkingHours() {
        var schedule = new Schedule(houseScheduleId, defaultWorkingHours); //an empty schedule

        assertThrows(IllegalArgumentException.class, () ->
                schedule.scheduleAppointment(LocalTime.of(9, 30), Duration.ofMinutes(60), "a1")
        );
        assertThrows(IllegalArgumentException.class, () ->
                schedule.scheduleAppointment(LocalTime.of(17, 50), Duration.ofMinutes(11), "a1")
        );
        assertEquals(0, schedule.timeSlots().size());
    }

    @Test
    public void serializeId() {
        assertEquals("house:2021-01-01", houseScheduleId.toString());

        assertEquals(houseScheduleId, Schedule.ScheduleId.fromString("house:2021-01-01"));
    }

    private Schedule.TimeSlot appointment(String startTime, String endTime) {
        return new Schedule.TimeSlot(LocalTime.parse(startTime), LocalTime.parse(endTime), "a1");
    }
}