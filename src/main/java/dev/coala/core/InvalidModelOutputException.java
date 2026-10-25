package dev.coala.core;

/** Model output could not be parsed or did not satisfy the requested structured contract. */
public final class InvalidModelOutputException extends DecisionProcedureException {
    private static final long serialVersionUID = 1L;

    public InvalidModelOutputException(String message) {
        super(message);
    }

    public InvalidModelOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
