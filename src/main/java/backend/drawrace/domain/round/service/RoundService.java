package backend.drawrace.domain.round.service;

import java.util.List;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.domain.round.dto.CurrentRoundResponse;
import backend.drawrace.domain.round.dto.RoundParticipantResponse;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundParticipant;
import backend.drawrace.domain.round.entity.RoundSubmission;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.repository.RoundSubmissionRepository;
import backend.drawrace.domain.round.validator.RoundValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoundService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final RoundRepository roundRepository;
    private final RoundParticipantRepository roundParticipantRepository;
    private final RoundSubmissionRepository roundSubmissionRepository;
    private final KeywordProvider keywordProvider;
    private final RoundValidator roundValidator;
    private final AiInferenceService aiInferenceService;

    /**
     * 게임 시작 처리
     * - 방 상태와 참가자 수를 검증
     * - 첫 라운드를 생성하고 시작 상태로 변경
     * - 현재 방 참가자 전원을 첫 라운드 참가자로 등록
     */
    @Transactional
    public RoundStartResponse startGame(Long roomId, Long userId) {
        Room room = roomRepository
                .findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 방입니다. roomId=" + roomId));

        long participantCount = participantRepository.countByRoomId(roomId);

        roundValidator.validateStartGame(
                room, participantCount, roundRepository.findByRoomIdAndIsActiveTrue(roomId), userId);

        String keyword = keywordProvider.getRandomKeyword();

        Round firstRound = Round.create(room, 1, keyword);
        firstRound.start();
        room.startGame();

        Round savedRound = roundRepository.save(firstRound);

        List<Participant> participants = participantRepository.findByRoomId(roomId);
        saveRoundParticipants(savedRound, participants);

        return RoundStartResponse.from(savedRound);
    }

    /**
     * 그림 제출 처리
     * - 현재 라운드와 참가자 유효성을 검증
     * - 중복 제출을 막고 제출 기록을 저장
     * - 아직 전원 제출 전이면 대기 응답
     * - 전원 제출 완료면 라운드 승자를 선정하고 다음 상태를 결정
     */
    @Transactional
    public SubmitDrawingResponse submitDrawing(Long roundId, SubmitDrawingRequest request) {
        // 1. 현재 라운드 조회 및 진행 상태 검증
        Round round = roundRepository
                .findById(roundId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 라운드입니다. roundId=" + roundId));

        roundValidator.validateRoundInProgress(round);

        // 2. 방 소속 참가자인지 검증
        Participant participant = getValidParticipant(round, request.getParticipantId());

        // 3. 이번 라운드 제출 대상인지 검증
        boolean canPlay =
                roundParticipantRepository.existsByRoundIdAndParticipantId(round.getId(), participant.getId());
        roundValidator.validateRoundParticipant(canPlay, participant.getId());

        // 4. 이미 제출했는지 검증
        boolean alreadySubmitted =
                roundSubmissionRepository.existsByRoundIdAndParticipantId(round.getId(), participant.getId());
        if (alreadySubmitted) {
            throw new IllegalStateException("이미 제출을 완료한 참가자입니다. participantId=" + participant.getId());
        }

        // 5. AI 판독
        AiInferenceResponse aiResult = aiInferenceService.infer(request.getImageData(), round.getKeyword());

        // 6. 제출 기록 저장
        RoundSubmission submission = RoundSubmission.create(
                round, participant, request.getImageData(), aiResult.getAiAnswer(), aiResult.getScore());
        roundSubmissionRepository.save(submission);

        // 7. 현재 제출 수 확인
        long submittedCount = roundSubmissionRepository.countByRoundId(round.getId());
        long totalParticipantCount = roundParticipantRepository.countByRoundId(round.getId());

        // 8. 아직 전원 제출 전이면 대기 응답
        if (submittedCount < totalParticipantCount) {
            return SubmitDrawingResponse.builder()
                    .roundId(round.getId())
                    .aiAnswer(aiResult.getAiAnswer())
                    .score(aiResult.getScore())
                    .submittedCount((int) submittedCount)
                    .totalParticipantCount((int) totalParticipantCount)
                    .roundFinished(false)
                    .gameFinished(false)
                    .tieBreakerStarted(false)
                    .build();
        }

        // 9. 전원 제출 완료면 라운드 종료 처리
        return handleRoundCompletion(round, aiResult, submittedCount, totalParticipantCount);
    }

    /**
     * 현재 진행 중인 라운드 조회
     * - 방의 활성 라운드를 찾고
     * - 해당 라운드 참가자 목록을 DTO로 변환해서 반환
     */
    @Transactional(readOnly = true)
    public CurrentRoundResponse getCurrentRound(Long roomId) {
        Round currentRound = roundRepository
                .findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new EntityNotFoundException("현재 진행 중인 라운드가 없습니다. roomId=" + roomId));

        List<RoundParticipantResponse> participants =
                roundParticipantRepository.findByRoundId(currentRound.getId()).stream()
                        .map(roundParticipant -> RoundParticipantResponse.from(roundParticipant.getParticipant()))
                        .toList();

        return CurrentRoundResponse.of(currentRound, participants);
    }

    /**
     * 현재 라운드의 방에 속한 참가자를 조회한다.
     * - 방 소속이 아니면 예외 발생
     */
    private Participant getValidParticipant(Round round, Long participantId) {
        return participantRepository
                .findByIdAndRoomId(participantId, round.getRoom().getId())
                .orElseThrow(
                        () -> new EntityNotFoundException("해당 라운드의 방에 속한 참가자가 아닙니다. participantId=" + participantId));
    }

    /**
     * 전원 제출 완료 시 이번 라운드의 승자를 선정하고 라운드를 종료한다.
     */
    private SubmitDrawingResponse handleRoundCompletion(
            Round round, AiInferenceResponse aiResult, long submittedCount, long totalParticipantCount) {
        List<RoundSubmission> submissions = roundSubmissionRepository.findByRoundId(round.getId());

        RoundSubmission winnerSubmission = submissions.stream()
                .max((a, b) -> Double.compare(a.getScore(), b.getScore()))
                .orElseThrow(() -> new IllegalStateException("제출 기록이 없습니다. roundId=" + round.getId()));

        Participant roundWinner = winnerSubmission.getParticipant();
        roundWinner.increaseRoundWinCount();
        round.finish();

        return handleAfterRoundFinished(round, aiResult, submittedCount, totalParticipantCount, roundWinner);
    }

    /**
     * 라운드 종료 후 다음 상태를 결정한다.
     * - 결승 라운드면 최종 우승 처리
     * - 일반 라운드면 다음 라운드 / 마지막 라운드 처리 분기
     */
    private SubmitDrawingResponse handleAfterRoundFinished(
            Round round,
            AiInferenceResponse aiResult,
            long submittedCount,
            long totalParticipantCount,
            Participant roundWinner) {
        Room room = round.getRoom();

        // 결승 라운드면 바로 최종 우승
        if (round.isTiebreaker()) {
            roundWinner.markWinner();
            room.finishGame();
            recordGameResults(room, roundWinner);

            return SubmitDrawingResponse.builder()
                    .roundId(round.getId())
                    .aiAnswer(aiResult.getAiAnswer())
                    .score(aiResult.getScore())
                    .submittedCount((int) submittedCount)
                    .totalParticipantCount((int) totalParticipantCount)
                    .roundFinished(true)
                    .gameFinished(true)
                    .tieBreakerStarted(false)
                    .roundWinnerParticipantId(roundWinner.getId())
                    .finalWinnerParticipantId(roundWinner.getId())
                    .build();
        }

        // 일반 라운드 + 아직 다음 라운드가 남아 있으면 다음 라운드 생성
        if (round.getRoundNumber() < room.getTotalRounds()) {
            Round nextRound = createNextRound(room, round.getRoundNumber() + 1);

            List<Participant> participants = participantRepository.findByRoomId(room.getId());
            saveRoundParticipants(nextRound, participants);

            return SubmitDrawingResponse.builder()
                    .roundId(round.getId())
                    .aiAnswer(aiResult.getAiAnswer())
                    .score(aiResult.getScore())
                    .submittedCount((int) submittedCount)
                    .totalParticipantCount((int) totalParticipantCount)
                    .roundFinished(true)
                    .gameFinished(false)
                    .tieBreakerStarted(false)
                    .roundWinnerParticipantId(roundWinner.getId())
                    .nextRoundId(nextRound.getId())
                    .nextRoundNumber(nextRound.getRoundNumber())
                    .nextRoundTieBreaker(false)
                    .build();
        }

        // 마지막 일반 라운드면 최종 우승 또는 결승 생성 처리
        return handleLastNormalRound(round, aiResult, submittedCount, totalParticipantCount, roundWinner);
    }

    /**
     * 마지막 일반 라운드 처리
     * - 최고 점수자가 1명이면 최종 우승
     * - 여러 명이면 결승 라운드 생성
     */
    private SubmitDrawingResponse handleLastNormalRound(
            Round round,
            AiInferenceResponse aiResult,
            long submittedCount,
            long totalParticipantCount,
            Participant roundWinner) {
        Room room = round.getRoom();
        List<Participant> topScorers = findTopScorers(room.getId());

        // 단독 우승
        if (topScorers.size() == 1) {
            Participant finalWinner = topScorers.get(0);
            finalWinner.markWinner();
            room.finishGame();
            recordGameResults(room, finalWinner);

            return SubmitDrawingResponse.builder()
                    .roundId(round.getId())
                    .aiAnswer(aiResult.getAiAnswer())
                    .score(aiResult.getScore())
                    .submittedCount((int) submittedCount)
                    .totalParticipantCount((int) totalParticipantCount)
                    .roundFinished(true)
                    .gameFinished(true)
                    .tieBreakerStarted(false)
                    .roundWinnerParticipantId(roundWinner.getId())
                    .finalWinnerParticipantId(finalWinner.getId())
                    .build();
        }

        // 동점이면 결승 라운드 생성
        Round tieBreakerRound = createTieBreakerRound(room, round.getRoundNumber() + 1);
        saveRoundParticipants(tieBreakerRound, topScorers);

        return SubmitDrawingResponse.builder()
                .roundId(round.getId())
                .aiAnswer(aiResult.getAiAnswer())
                .score(aiResult.getScore())
                .submittedCount((int) submittedCount)
                .totalParticipantCount((int) totalParticipantCount)
                .roundFinished(true)
                .gameFinished(false)
                .tieBreakerStarted(true)
                .roundWinnerParticipantId(roundWinner.getId())
                .nextRoundId(tieBreakerRound.getId())
                .nextRoundNumber(tieBreakerRound.getRoundNumber())
                .nextRoundTieBreaker(true)
                .build();
    }

    /**
     * 특정 라운드의 참가자 명단 저장
     * - 일반 라운드면 전원
     * - 결승 라운드면 동점자만 등록
     */
    private void saveRoundParticipants(Round round, List<Participant> participants) {
        List<RoundParticipant> roundParticipants = participants.stream()
                .map(participant -> RoundParticipant.of(round, participant))
                .toList();

        roundParticipantRepository.saveAll(roundParticipants);
    }

    /**
     * 다음 일반 라운드 생성 및 시작
     */
    private Round createNextRound(Room room, int nextRoundNumber) {
        String keyword = keywordProvider.getRandomKeyword();

        Round nextRound = Round.create(room, nextRoundNumber, keyword);
        nextRound.start();

        return roundRepository.save(nextRound);
    }

    /**
     * 결승 라운드 생성 및 시작
     */
    private Round createTieBreakerRound(Room room, int nextRoundNumber) {
        String keyword = keywordProvider.getRandomKeyword();

        Round tieBreakerRound = Round.createTieBreaker(room, nextRoundNumber, keyword);
        tieBreakerRound.start();

        return roundRepository.save(tieBreakerRound);
    }

    /**
     * 게임 종료 시 전원 totalGameCount +1, 최종 우승자 winGameCount +1
     */
    private void recordGameResults(Room room, Participant winner) {
        List<Participant> participants = participantRepository.findByRoomId(room.getId());

        participants.forEach(p -> p.getUserId().getStats().recordGame());

        winner.getUserId().getStats().recordWin();
    }

    /**
     * 현재 방에서 최고 점수자 목록 조회
     * - 최종 우승 판정
     * - 결승 진출자 선정
     * 에 사용
     */
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
