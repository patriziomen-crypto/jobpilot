package it.patrizio.jobpilot.search;

import it.patrizio.jobpilot.profile.ProfileSearch;

import java.util.Arrays;
import java.util.List;

/** Cosa cercare e dove, per una persona. Specchio di ProfileSearch per il frontend. */
public record SearchSettings(
        List<String> keywords,
        String location,
        boolean remote,
        int days,
        boolean linkedin,
        boolean indeed,
        boolean autoAnalyze,
        int maxPerQuery,
        boolean scheduled
) {
    public static SearchSettings from(ProfileSearch s) {
        List<String> keywords = s.getKeywords() == null ? List.of()
                : Arrays.stream(s.getKeywords().split("\\R")).map(String::strip).filter(k -> !k.isBlank()).toList();
        return new SearchSettings(keywords, s.getLocation(), s.isRemote(), s.getDays(), s.isLinkedin(), s.isIndeed(),
                s.isAutoAnalyze(), s.getMaxPerQuery(), s.isScheduled());
    }

    public void applyTo(ProfileSearch s) {
        s.setKeywords(keywords == null ? "" : String.join("\n", keywords.stream().map(String::strip).filter(k -> !k.isBlank()).toList()));
        s.setLocation(location == null || location.isBlank() ? "Italia" : location.strip());
        s.setRemote(remote);
        s.setDays(Math.max(1, Math.min(30, days)));
        s.setLinkedin(linkedin);
        s.setIndeed(indeed);
        s.setAutoAnalyze(autoAnalyze);
        s.setMaxPerQuery(Math.max(1, Math.min(50, maxPerQuery)));
        s.setScheduled(scheduled);
    }
}
