package dev.coala.core.action;

/** Uncalibrated quality assessment for one proposed action or plan artifact. */
public record ActionQualityEvaluation(boolean accepted, double score, String feedback) {
    public ActionQualityEvaluation {
        if (!Double.isFinite(score) || score < 0 || score > 1) throw new IllegalArgumentException("score must be in [0,1]");
        feedback = feedback == null ? "" : feedback;
        if (feedback.length() > 1_000) feedback = feedback.substring(0, 997) + "...";
    }
}
