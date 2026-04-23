package backend.drawrace.domain.room.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.user.entity.User;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    List<Participant> findByRoomId(Long roomId);

    long countByRoomId(Long roomId);

    Optional<Participant> findByIdAndRoomId(Long participantId, Long roomId);

    Optional<Participant> findByRoomAndUserId(Room room, User user);
}
