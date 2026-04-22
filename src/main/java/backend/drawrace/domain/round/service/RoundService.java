package backend.drawrace.domain.round.service;

import backend.drawrace.domain.round.entity.RoundParticipant;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.validator.RoundValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoundService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final RoundRepository roundRepository;
    private final KeywordProvider keywordProvider;
    private final RoundValidator roundValidator;
    private final AiInferenceService aiInferenceService;
    private final RoundParticipantRepository roundParticipantRepository;

    @Transactional
    public RoundStartResponse startGame(Long roomId) {
        Room room = roomRepository
                .findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 방입니다. roomId=" + roomId));

        long participantCount = participantRepository.countByRoomId(roomId);

        roundValidator.validateStartGame(room, participantCount, roundRepository.findByRoomIdAndIsActiveTrue(roomId));

        String keyword = keywordProvider.getRandomKeyword();

        Round firstRound = Round.create(room, 1, keyword);
        firstRound.start();

        room.startGame();

        Round savedRound = roundRepository.save(firstRound);

        List<Participant> participants = participantRepository.findByRoomId(roomId);

        List<RoundParticipant> roundParticipants = participants.stream()
                .map(participant -> RoundParticipant.of(savedRound, participant))
                .toList();

        roundParticipantRepository.saveAll(roundParticipants);

        return RoundStartResponse.from(savedRound);
    }

    @Transactional
    public SubmitDrawingResponse submitDrawing(Long roundId, SubmitDrawingRequest request) {
        Round round = roundRepository
                .findById(roundId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 라운드입니다. roundId=" + roundId));

        roundValidator.validateRoundInProgress(round);

        Participant participant = participantRepository
                .findByIdAndRoomId(request.getParticipantId(), round.getRoom().getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "해당 라운드의 방에 속한 참가자가 아닙니다. participantId=" + request.getParticipantId()));

        boolean canPlay = roundParticipantRepository
                .existsByRoundIdAndParticipantId(round.getId(), participant.getId());

        if (!canPlay) {
            throw new IllegalStateException("이번 라운드 참가 대상이 아닙니다. participantId=" + participant.getId());
        }

        String aiAnswer = aiInferenceService.infer(request.getImageData());

        boolean correct = round.getKeyword().equals(aiAnswer);

        if (correct) {
            participant.increaseRoundWinCount();
            round.finish();
        }

        return SubmitDrawingResponse.builder()
                .roundId(round.getId())
                .aiAnswer(aiAnswer)
                .correct(correct)
                .keyword(round.getKeyword())
                .roundWinCount(participant.getRoundWinCount())
                .build();
    }
}
