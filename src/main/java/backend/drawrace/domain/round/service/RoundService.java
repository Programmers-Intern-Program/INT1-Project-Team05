package backend.drawrace.domain.round.service;

import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.validator.RoundValidator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoundService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final RoundRepository roundRepository;
    private final KeywordProvider keywordProvider;
    private final RoundValidator roundValidator;

    @Transactional
    public Round startGame(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 방입니다. roomId=" + roomId));

        long participantCount = participantRepository.countByRoomId(roomId);

        roundValidator.validateStartGame(
                room,
                participantCount,
                roundRepository.findByRoomIdAndIsActiveTrue(roomId)
        );

        String keyword = keywordProvider.getRandomKeyword();

        Round firstRound = Round.create(room, 1, keyword);
        firstRound.start();

        room.startGame();

        return roundRepository.save(firstRound);
    }
}