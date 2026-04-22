package backend.drawrace.domain.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import backend.drawrace.domain.room.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {}
