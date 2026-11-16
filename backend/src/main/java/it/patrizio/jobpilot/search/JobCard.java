package it.patrizio.jobpilot.search;

import it.patrizio.jobpilot.jobs.JobSource;

/** Un annuncio come appare nei risultati di ricerca, prima di aprirne il dettaglio. */
public record JobCard(JobSource source, String url, String title, String company, String location) {}
