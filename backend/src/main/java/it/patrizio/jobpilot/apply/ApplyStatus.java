package it.patrizio.jobpilot.apply;

import java.time.LocalDateTime;
import java.util.List;

/** Stato dell'invio assistito in corso (o dell'ultimo). */
public record ApplyStatus(
        boolean running,
        boolean windowOpen,
        Long applicationId,
        String jobTitle,
        ApplyOutcome outcome,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<String> unanswered,
        List<String> answered,
        List<String> log
) {}
