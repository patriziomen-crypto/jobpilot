package it.patrizio.jobpilot.profile;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Impostazioni della ricerca automatica di una persona (parole chiave, zona, fonti). */
@Entity
@Table(name = "profile_search")
@Getter
@Setter
@NoArgsConstructor
public class ProfileSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    /** Una parola chiave per riga. */
    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(nullable = false, length = 160)
    private String location = "Roma, Lazio, Italia";

    @Column(nullable = false)
    private boolean remote = true;

    @Column(nullable = false)
    private int days = 7;

    @Column(nullable = false)
    private boolean linkedin = true;

    @Column(nullable = false)
    private boolean indeed = true;

    @Column(name = "auto_analyze", nullable = false)
    private boolean autoAnalyze = true;

    @Column(name = "max_per_query", nullable = false)
    private int maxPerQuery = 10;

    @Column(nullable = false)
    private boolean scheduled;
}
