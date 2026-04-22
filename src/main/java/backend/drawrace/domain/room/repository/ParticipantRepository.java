package backend.drawrace.domain.room.repository;

import backend.drawrace.domain.room.entity.Participant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    List<Participant> findByRoomId(Long roomId);
    long countByRoomId(Long roomId);
    Optional<Participant> findByIdAndRoomId(Long participantId, Long roomId);
}