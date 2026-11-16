package it.patrizio.jobpilot.profile;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Risposta pronta a una domanda ricorrente dei form di candidatura (Easy Apply, Indeed). */
@Entity
@Table(name = "standard_answer")
@Getter
@Setter
@NoArgsConstructor
public class StandardAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
