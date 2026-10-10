package com.example.network.exception;

/** Base class for errors that map directly to an HTTP status + {"error": "..."} body. */
public abstract class ApiException extends RuntimeException {

    private final int status;

    protected ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
