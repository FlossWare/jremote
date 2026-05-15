package org.flossware.jremote;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RemoteException extends RuntimeException {
    private final String originalExceptionType;
    private final String originalMessage;
    private final StackTraceElement[] originalStackTrace;

    @JsonCreator
    public RemoteException(
        @JsonProperty("originalExceptionType") String originalExceptionType,
        @JsonProperty("originalMessage") String originalMessage,
        @JsonProperty("originalStackTrace") StackTraceElement[] originalStackTrace
    ) {
        super("Remote exception: " + originalExceptionType + ": " + originalMessage);
        this.originalExceptionType = originalExceptionType;
        this.originalMessage = originalMessage;
        this.originalStackTrace = originalStackTrace;
        if (originalStackTrace != null) {
            setStackTrace(originalStackTrace);
        }
    }

    public static RemoteException fromThrowable(Throwable t) {
        return new RemoteException(
            t.getClass().getName(),
            t.getMessage(),
            t.getStackTrace()
        );
    }

    public String getOriginalExceptionType() {
        return originalExceptionType;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public StackTraceElement[] getOriginalStackTrace() {
        return originalStackTrace;
    }

    @Override
    @JsonIgnore
    public String getMessage() {
        return super.getMessage();
    }

    @Override
    @JsonIgnore
    public String getLocalizedMessage() {
        return super.getLocalizedMessage();
    }

    @Override
    @JsonIgnore
    public Throwable getCause() {
        return super.getCause();
    }

    @Override
    @JsonIgnore
    public synchronized Throwable initCause(Throwable cause) {
        return super.initCause(cause);
    }
}
