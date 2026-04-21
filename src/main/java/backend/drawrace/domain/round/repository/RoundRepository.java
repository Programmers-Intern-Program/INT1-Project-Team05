package backend.drawrace.domain.round.repository;

import backend.drawrace.domain.round.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {
    Optional<Round> findByRoomIdAndIsActiveTrue(Long roomId);
    Optional<Round> findTopByRoomIdOrderByRoundNumberDesc(Long roomId);
}