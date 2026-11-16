package it.patrizio.jobpilot.search;

import java.time.LocalDateTime;
import java.util.List;

/** Stato della ricerca in corso (o dell'ultima), mostrato dal frontend. */
public record SearchStatus(
        boolean running,
        Long profileId,
        String profileName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int found,
        int imported,
        int analyzed,
        int errors,
        String current,
        List<String> log
) {}
