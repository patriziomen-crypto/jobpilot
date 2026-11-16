package it.patrizio.jobpilot.applications;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByJobOfferIdAndProfileId(Long jobOfferId, Long profileId);

    /** Carica offerta e profilo nella stessa query: evita una query per ogni scheda della dashboard. */
    @EntityGraph(attributePaths = {"jobOffer", "profile"})
    List<Application> findAllByProfileIdOrderByUpdatedAtDesc(Long profileId);

    @EntityGraph(attributePaths = {"jobOffer", "profile"})
    Optional<Application> findByIdAndProfileId(Long id, Long profileId);

    long countByJobOfferId(Long jobOfferId);
}
