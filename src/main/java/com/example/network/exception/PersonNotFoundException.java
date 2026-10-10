package com.example.network.exception;

public class PersonNotFoundException extends ApiException {
    public PersonNotFoundException(long id) {
        super(404, "no person " + id);
    }
}
