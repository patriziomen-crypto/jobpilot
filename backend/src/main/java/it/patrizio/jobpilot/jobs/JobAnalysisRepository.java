package it.patrizio.jobpilot.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobAnalysisRepository extends JpaRepository<JobAnalysis, Long> {

    Optional<JobAnalysis> findByApplicationId(Long applicationId);

    List<JobAnalysis> findAllByApplicationProfileId(Long profileId);
}
