package com.copa.ticketing.domain;

public record Customer(
        long id,
        String fullName,
        String email,
        String documentType,
        String documentNumber,
        String phone
) {}
