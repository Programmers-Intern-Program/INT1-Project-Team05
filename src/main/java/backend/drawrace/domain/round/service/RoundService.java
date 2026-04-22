package backend.drawrace.domain.round.service;

import backend.drawrace.domain.round.dto.CurrentRoundResponse;
import backend.drawrace.domain.round.dto.RoundParticipantResponse;
import java.util.List;

import jakarta.persistence.EntityNotFoundException;

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
import backend.drawrace.domain.round.entity.RoundParticipant;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
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
        saveRoundParticipants(savedRound, participants);

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

        boolean canPlay =
                roundParticipantRepository.existsByRoundIdAndParticipantId(round.getId(), participant.getId());

        if (!canPlay) {
            throw new IllegalStateException("이번 라운드 참가 대상이 아닙니다. participantId=" + participant.getId());
        }

        String aiAnswer = aiInferenceService.infer(request.getImageData());

        boolean correct = round.getKeyword().equals(aiAnswer);

        if (correct) {
            participant.increaseRoundWinCount();
            round.finish();

            Room room = round.getRoom();

            // 1. 일반 라운드이고, 아직 다음 일반 라운드가 남아 있는 경우
            if (!round.isTiebreaker() && round.getRoundNumber() < room.getTotalRounds()) {
                Round nextRound = createNextRound(room, round.getRoundNumber() + 1);

                List<Participant> participants = participantRepository.findByRoomId(room.getId());
                saveRoundParticipants(nextRound, participants);
            }

            // 2. 일반 라운드이고, 마지막 일반 라운드인 경우
            else if (!round.isTiebreaker() && round.getRoundNumber() == room.getTotalRounds()) {
                List<Participant> topScorers = findTopScorers(room.getId());

                // 단독 1등
                if (topScorers.size() == 1) {
                    Participant winner = topScorers.get(0);
                    winner.markWinner();
                    room.finishGame();
                }
                // 동점자 발생 -> 결승 라운드
                else {
                    Round tieBreakerRound = createTieBreakerRound(room, round.getRoundNumber() + 1);
                    saveRoundParticipants(tieBreakerRound, topScorers);
                }
            }

            // 3. 결승 라운드인 경우
            else if (round.isTiebreaker()) {
                participant.markWinner();
                room.finishGame();
            }
        }

        return SubmitDrawingResponse.builder()
                .roundId(round.getId())
                .aiAnswer(aiAnswer)
                .correct(correct)
                .keyword(round.getKeyword())
                .roundWinCount(participant.getRoundWinCount())
                .build();
    }

    @Transactional(readOnly = true)
    public CurrentRoundResponse getCurrentRound(Long roomId) {
        Round currentRound = roundRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new EntityNotFoundException("현재 진행 중인 라운드가 없습니다. roomId=" + roomId));

        List<RoundParticipantResponse> participants = roundParticipantRepository.findByRoundId(currentRound.getId())
                .stream()
                .map(roundParticipant -> RoundParticipantResponse.from(roundParticipant.getParticipant()))
                .toList();

        return CurrentRoundResponse.of(currentRound, participants);
    }

    private void saveRoundParticipants(Round round, List<Participant> participants) {
        List<RoundParticipant> roundParticipants = participants.stream()
                .map(participant -> RoundParticipant.of(round, participant))
                .toList();

        roundParticipantRepository.saveAll(roundParticipants);
    }

    private Round createNextRound(Room room, int nextRoundNumber) {
        String keyword = keywordProvider.getRandomKeyword();

        Round nextRound = Round.create(room, nextRoundNumber, keyword);
        nextRound.start();

        return roundRepository.save(nextRound);
    }

    private Round createTieBreakerRound(Room room, int nextRoundNumber) {
        String keyword = keywordProvider.getRandomKeyword();

        Round tieBreakerRound = Round.createTieBreaker(room, nextRoundNumber, keyword);
        tieBreakerRound.start();

        return roundRepository.save(tieBreakerRound);
    }

    private List<Participant> findTopScorers(Long roomId) {
        List<Participant> participants = participantRepository.findByRoomId(roomId);

        int maxWinCount = participants.stream()
                .mapToInt(Participant::getRoundWinCount)
                .max()
                .orElse(0);

        return participants.stream()
                .filter(participant -> participant.getRoundWinCount() == maxWinCount)
                .toList();
    }
}
