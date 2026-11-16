package it.patrizio.jobpilot.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {

    Optional<JobOffer> findByUrlHash(String urlHash);
}
