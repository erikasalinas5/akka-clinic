package com.clinic.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.clinic.domain.Schedule;
import org.junit.jupiter.api.Test;

import static com.clinic.application.DateUtils.time;
import static org.junit.jupiter.api.Assertions.*;

public class ScheduleEntityTest {

    @Test
    public void checkOverlapping() {
        var testKit = KeyValueEntityTestKit.of("house:2031-10-20", ScheduleEntity::new);
        {
            var result = testKit
                    .method(ScheduleEntity::createSchedule)
                    .invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));
            assertTrue(result.isReply());
            assertTrue(result.stateWasUpdated());
            assertTrue(((Schedule) result.getUpdatedState()).timeSlots().isEmpty());
        }

        {
            var result = testKit.method(ScheduleEntity::createSchedule).invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));
            assertTrue(result.isError());
            assertFalse(result.stateWasUpdated());
        }
    }
}
