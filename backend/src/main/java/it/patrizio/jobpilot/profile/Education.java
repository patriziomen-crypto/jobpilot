package it.patrizio.jobpilot.profile;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un titolo di studio o una certificazione del profilo master. */
@Entity
@Table(name = "education")
@Getter
@Setter
@NoArgsConstructor
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(nullable = false, length = 160)
    private String institution;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "period_label", length = 80)
    private String periodLabel;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
