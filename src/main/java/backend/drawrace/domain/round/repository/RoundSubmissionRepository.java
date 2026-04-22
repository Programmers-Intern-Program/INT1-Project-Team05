package backend.drawrace.domain.round.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import backend.drawrace.domain.round.entity.RoundSubmission;

public interface RoundSubmissionRepository extends JpaRepository<RoundSubmission, Long> {

    boolean existsByRoundIdAndParticipantId(Long roundId, Long participantId);

    long countByRoundId(Long roundId);

    List<RoundSubmission> findByRoundId(Long roundId);
}
