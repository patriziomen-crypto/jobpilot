package it.patrizio.jobpilot.jobs;

import it.patrizio.jobpilot.applications.Application;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/** Risultato dell'analisi di un'offerta rispetto al profilo della persona (una per candidatura). */
@Entity
@Table(name = "job_analysis")
@Getter
@Setter
@NoArgsConstructor
public class JobAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    /** Compatibilità 0-100 tra profilo e offerta. */
    @Column(nullable = false)
    private int score;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_requirements")
    private List<String> matchedRequirements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_requirements")
    private List<String> missingRequirements;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> keywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "red_flags")
    private List<String> redFlags;

    @Column(length = 10)
    private String language;

    @Column(length = 60)
    private String model;

    /** Chi ha prodotto l'analisi: es. "Claude", "OpenAI", "Modalità base". */
    @Column(length = 60)
    private String provider;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
