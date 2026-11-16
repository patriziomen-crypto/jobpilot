package it.patrizio.jobpilot.jobs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Un'offerta di lavoro importata da LinkedIn, Indeed o altra fonte. */
@Entity
@Table(name = "job_offer")
@Getter
@Setter
@NoArgsConstructor
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobSource source = JobSource.ALTRO;

    @Column(length = 1000)
    private String url;

    /** SHA-256 dell'URL normalizzato: serve a non importare due volte la stessa offerta. */
    @Column(name = "url_hash", length = 64)
    private String urlHash;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 160)
    private String company;

    @Column(length = 160)
    private String location;

    @Column(nullable = false)
    private boolean remote;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(length = 10)
    private String language;

    @CreationTimestamp
    @Column(name = "found_at", updatable = false)
    private LocalDateTime foundAt;
}
