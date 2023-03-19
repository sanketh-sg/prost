package de.unibamberg.dsam.group6.prost.util.exception;

public class CallFailedException extends Exception {
    public CallFailedException() {
        super();
    }

    public CallFailedException(String message) {
        super(message);
    }

    public CallFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public CallFailedException(Throwable cause) {
        super(cause);
    }

    protected CallFailedException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
