package it.patrizio.jobpilot.profile;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un'esperienza lavorativa del profilo master. I punti elenco sono nel campo bullets, uno per riga. */
@Entity
@Table(name = "experience")
@Getter
@Setter
@NoArgsConstructor
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(nullable = false, length = 160)
    private String company;

    @Column(nullable = false, length = 160)
    private String role;

    @Column(name = "period_label", length = 80)
    private String periodLabel;

    @Column(columnDefinition = "TEXT")
    private String bullets;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
