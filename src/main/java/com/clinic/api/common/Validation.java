package com.clinic.api.common;

import akka.javasdk.http.HttpException;

import java.time.LocalDate;
import java.time.LocalTime;

public class Validation {
    public static LocalDate parseDate(String day) {
        try {
            return LocalDate.parse(day);
        } catch (Exception e) {
            throw HttpException.badRequest("Invalid date format");
        }
    }

    public static LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time);
        } catch (Exception e) {
            throw HttpException.badRequest("Invalid time format");
        }
    }
}