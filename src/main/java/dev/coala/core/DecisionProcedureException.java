package dev.coala.core;

public class DecisionProcedureException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public DecisionProcedureException(String message) {
        super(message);
    }

    public DecisionProcedureException(String message, Throwable cause) {
        super(message, cause);
    }
}
