package com.example.network.exception;

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(400, message);
    }
}
