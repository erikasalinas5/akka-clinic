package com.clinic.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class DateUtils {
    public static LocalDate date(String str) {
        return LocalDate.parse(str);
    }

    public static LocalTime time(String str) {
        return LocalTime.parse(str);
    }

    public static LocalDateTime dateTime(String str) {
        return LocalDateTime.parse(str);
    }
}