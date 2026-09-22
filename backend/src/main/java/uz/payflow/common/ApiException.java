package uz.payflow.common;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that are part of the API contract. Each one carries a stable machine-readable
 * {@code code} that clients can branch on, and a human-readable message.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
