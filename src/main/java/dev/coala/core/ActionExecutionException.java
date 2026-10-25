package dev.coala.core;

/** A selected, validated action failed while executing against its environment adapter. */
public final class ActionExecutionException extends DecisionProcedureException {
    private static final long serialVersionUID = 1L;

    public ActionExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
