package backend.drawrace.domain.round.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import backend.drawrace.domain.round.entity.Round;

public interface RoundRepository extends JpaRepository<Round, Long> {
    Optional<Round> findByRoomIdAndIsActiveTrue(Long roomId);

    Optional<Round> findTopByRoomIdOrderByRoundNumberDesc(Long roomId);
}
