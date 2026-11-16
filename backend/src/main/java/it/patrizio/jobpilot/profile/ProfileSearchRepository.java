package it.patrizio.jobpilot.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileSearchRepository extends JpaRepository<ProfileSearch, Long> {

    Optional<ProfileSearch> findByProfileId(Long profileId);

    List<ProfileSearch> findAllByScheduledTrue();
}
