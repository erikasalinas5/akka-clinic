package com.clinic.domain;

import java.util.List;
import java.util.Optional;

public record Doctor(
        String id,
        String firstName,
        String lastName,
        List<String> specialities,
        String description,
        Optional<Contact> contact
) {
    public record Contact(Optional<String> phone, Optional<String> email) {
    }
}