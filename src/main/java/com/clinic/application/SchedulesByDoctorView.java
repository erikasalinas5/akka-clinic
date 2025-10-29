package com.clinic.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.clinic.domain.Schedule;

import java.util.List;

@Component(id = "schedules-by-doctor")
public class SchedulesByDoctorView extends View {

    public record ScheduleRow(String doctorId, String date, List<TimeSlot> slots) {
        public record TimeSlot(String startTime, String endTime) {}
    }

    @Table("schedules")
    @Consume.FromKeyValueEntity(ScheduleEntity.class)
    public static class Updater extends TableUpdater<ScheduleRow> {
        public Effect<ScheduleRow> onChange(Schedule schedule) {
            var slots = schedule.timeSlots().stream().map(slot -> new ScheduleRow.TimeSlot(slot.startTime().toString(), slot.endTime().toString())).toList();
            var row = new ScheduleRow(schedule.id().doctorId(), schedule.id().date().toString(), slots);
            return effects().updateRow(row);
        }
    }

    public record ScheduleRows(List<ScheduleRow> schedules){}

    @Query("SELECT * as schedules FROM schedules WHERE doctorId = :doctorId")
    public QueryEffect<ScheduleRows> getSchedules(String doctorId) {
        return queryResult();
    }


    public record ScheduleSummary(String doctorId, String date){}
    public record ScheduleSummaries(List<ScheduleSummary> schedules){}

    public record FindScheduleSummary(String doctorId, String fromDate, String toDate){}

    @Query("SELECT (doctorId, date) AS schedules FROM schedules WHERE doctorId = :doctorId AND date > :fromDate AND date < :toDate")
    public QueryEffect<ScheduleSummaries> getSummaries(FindScheduleSummary query){
        return queryResult();
    }
}
