package backend.drawrace.domain.round.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import backend.drawrace.domain.round.entity.RoundParticipant;

public interface RoundParticipantRepository extends JpaRepository<RoundParticipant, Long> {

    boolean existsByRoundIdAndParticipantId(Long roundId, Long participantId);

    long countByRoundId(Long roundId);

    List<RoundParticipant> findByRoundId(Long roundId);
}
