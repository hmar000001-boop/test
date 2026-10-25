package dev.coala.api;

import dev.coala.core.ActionExecutionException;
import dev.coala.core.DecisionProcedureException;
import dev.coala.core.InvalidModelOutputException;
import dev.coala.core.PlanningExhaustedException;
import dev.coala.llm.LanguageModelException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Invalid request");
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableRequest(HttpMessageNotReadableException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The request body must be valid JSON and satisfy the request schema.");
        detail.setTitle("Invalid request body");
        return detail;
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail sessionNotFound(NoSuchElementException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Session not found");
        return detail;
    }

    @ExceptionHandler(LanguageModelException.class)
    ProblemDetail languageModelFailure(LanguageModelException ex) {
        // Do not expose provider URLs, credentials, response bodies, or nested exception details.
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The configured language-model provider could not complete the request.");
        detail.setTitle("Language-model provider failure");
        return detail;
    }

    @ExceptionHandler(InvalidModelOutputException.class)
    ProblemDetail invalidModelOutput(InvalidModelOutputException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The language-model response did not satisfy the required structured-output contract.");
        detail.setTitle("Invalid language-model response");
        return detail;
    }

    @ExceptionHandler(ActionExecutionException.class)
    ProblemDetail actionExecutionFailure(ActionExecutionException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The selected action failed while executing.");
        detail.setTitle("Action execution failed");
        return detail;
    }

    @ExceptionHandler(PlanningExhaustedException.class)
    ProblemDetail planningExhausted(PlanningExhaustedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The agent could not select an action within its configured planning-attempt limit.");
        detail.setTitle("Planning exhausted");
        return detail;
    }

    @ExceptionHandler(DecisionProcedureException.class)
    ProblemDetail decisionFailure(DecisionProcedureException ex) {
        // This is an application/agent failure, not a user input validation error.
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The agent decision procedure could not complete the request.");
        detail.setTitle("Agent execution failed");
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedFailure(Exception ex) {
        // Catch-all response intentionally avoids leaking exception messages or integration details.
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error prevented the request from completing.");
        detail.setTitle("Internal server error");
        return detail;
    }
}
