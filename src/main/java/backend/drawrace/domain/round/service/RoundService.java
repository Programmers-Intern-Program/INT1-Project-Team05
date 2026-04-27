package backend.drawrace.domain.round.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import backend.drawrace.global.exception.ServiceException;

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
    private final KeywordGenerator keywordGenerator;
    private final RoundValidator roundValidator;
    private final AiInferenceService aiInferenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 게임 시작 처리
     * - 방 상태와 참가자 수를 검증한다.
     * - 1라운드를 생성하고 참가자를 등록한다.
     */
    @Transactional
    public RoundStartResponse startGame(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 방입니다."));

        long participantCount = participantRepository.countByRoomId(roomId);

        roundValidator.validateStartGame(
                room, participantCount, roundRepository.findByRoomIdAndIsActiveTrue(roomId), userId);

        String keyword = keywordGenerator.generateKeyword();

        Round firstRound = Round.create(room, 1, keyword);
        firstRound.start();
        room.startGame();

        Round savedRound = roundRepository.save(firstRound);

        List<Participant> participants = participantRepository.findByRoomId(roomId);
        saveRoundParticipants(savedRound, participants);

        RoundStartResponse response = RoundStartResponse.from(savedRound);

        messagingTemplate.convertAndSend("/sub/rooms/" + roomId, response);

        return response;
    }

    /**
     * 그림 제출 처리
     * - 라운드/참가자 유효성을 검증한다.
     * - 제출을 저장하고, 전원 제출 시 라운드 종료 처리를 진행한다.
     */
    @Transactional
    public SubmitDrawingResponse submitDrawing(Long roundId, Long userId, SubmitDrawingRequest request) {
        Round round =
                roundRepository.findById(roundId).orElseThrow(() -> new ServiceException("404-2", "존재하지 않는 라운드입니다."));

        roundValidator.validateRoundInProgress(round);

        // 방 소속 참가자인지 확인
        Participant participant = getValidParticipant(round, request.getParticipantId());

        // 로그인한 사용자 본인의 참가 정보인지 확인
        roundValidator.validateParticipantOwner(participant, userId);

        // 이번 라운드 제출 대상인지 확인
        boolean canPlay =
                roundParticipantRepository.existsByRoundIdAndParticipantId(round.getId(), participant.getId());
        roundValidator.validateRoundParticipant(canPlay);

        // 이미 제출했는지 확인
        boolean alreadySubmitted =
                roundSubmissionRepository.existsByRoundIdAndParticipantId(round.getId(), participant.getId());
        roundValidator.validateNotSubmitted(alreadySubmitted);

        // AI 판독 수행
        // - 내부적으로 1회 재시도
        // - 최종 실패 시 예외를 던져 제출을 실패 처리한다
        AiInferenceResponse aiResult = aiInferenceService.infer(request.getImageData(), round.getKeyword());

        // 제출 기록 저장
        RoundSubmission submission = RoundSubmission.create(
                round, participant, request.getImageData(), aiResult.getAiAnswer(), aiResult.getScore());
        roundSubmissionRepository.save(submission);

        long submittedCount = roundSubmissionRepository.countByRoundId(round.getId());
        long totalParticipantCount = roundParticipantRepository.countByRoundId(round.getId());

        // 아직 전원 제출 전이면 대기 응답 반환
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

        // 전원 제출 완료 시 라운드 종료 처리
        SubmitDrawingResponse response = handleRoundCompletion(round, aiResult, submittedCount, totalParticipantCount);

        if (response.isRoundFinished()) {
            Long roomId = round.getRoom().getId();
            // 구독 경로: /sub/rooms/{roomId} 로 결과 전송
            messagingTemplate.convertAndSend("/sub/rooms/" + roomId, response);
        }

        return response;
    }

    /**
     * 현재 진행 중인 라운드를 조회한다.
     * - 방 참가자만 조회 가능하다.
     */
    @Transactional(readOnly = true)
    public CurrentRoundResponse getCurrentRound(Long roomId, Long userId) {
        validateRoomMember(roomId, userId);

        Round currentRound = roundRepository
                .findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new ServiceException("404-3", "현재 진행 중인 라운드가 없습니다."));

        List<RoundParticipantResponse> participants =
                roundParticipantRepository.findByRoundId(currentRound.getId()).stream()
                        .map(roundParticipant -> RoundParticipantResponse.from(roundParticipant.getParticipant()))
                        .toList();

        return CurrentRoundResponse.of(currentRound, participants);
    }

    /**
     * 해당 사용자가 방 참가자인지 확인한다.
     */
    private void validateRoomMember(Long roomId, Long userId) {
        boolean isRoomMember = participantRepository.existsByRoomIdAndUserId_Id(roomId, userId);

        if (!isRoomMember) {
            throw new ServiceException("403-4", "해당 방 참가자만 현재 라운드를 조회할 수 있습니다.");
        }
    }

    /**
     * 현재 라운드의 방에 속한 참가자를 조회한다.
     */
    private Participant getValidParticipant(Round round, Long participantId) {
        return participantRepository
                .findByIdAndRoomId(participantId, round.getRoom().getId())
                .orElseThrow(() -> new ServiceException("404-4", "해당 방에 속한 참가자가 아닙니다."));
    }

    /**
     * 전원 제출 완료 시 승자를 선정하고 라운드를 종료한다.
     */
    private SubmitDrawingResponse handleRoundCompletion(
            Round round, AiInferenceResponse aiResult, long submittedCount, long totalParticipantCount) {

        List<RoundSubmission> submissions = roundSubmissionRepository.findByRoundId(round.getId());

        RoundSubmission winnerSubmission = submissions.stream()
                .max((a, b) -> Double.compare(a.getScore(), b.getScore()))
                .orElseThrow(() -> new ServiceException("500-1", "제출 기록이 존재하지 않습니다."));

        Participant roundWinner = winnerSubmission.getParticipant();
        roundWinner.increaseRoundWinCount();
        round.finish();

        return handleAfterRoundFinished(round, aiResult, submittedCount, totalParticipantCount, roundWinner);
    }

    /**
     * 라운드 종료 후 다음 상태를 결정한다.
     * - 결승이면 게임 종료
     * - 일반 라운드가 남아 있으면 다음 라운드 생성
     * - 마지막 일반 라운드면 최종 우승 또는 결승으로 분기
     */
    private SubmitDrawingResponse handleAfterRoundFinished(
            Round round,
            AiInferenceResponse aiResult,
            long submittedCount,
            long totalParticipantCount,
            Participant roundWinner) {

        Room room = round.getRoom();

        // 결승 라운드가 끝난 경우 바로 최종 우승 처리
        if (round.isTiebreaker()) {
            roundWinner.markWinner();
            room.finishGame();

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

        // 아직 일반 라운드가 남아 있으면 다음 라운드 진행
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

        // 마지막 일반 라운드는 최종 우승 또는 결승 생성으로 처리
        return handleLastNormalRound(round, aiResult, submittedCount, totalParticipantCount, roundWinner);
    }

    /**
     * 마지막 일반 라운드 처리
     * - 단독 1등이면 최종 우승
     * - 동점이면 결승 라운드 생성
     */
    private SubmitDrawingResponse handleLastNormalRound(
            Round round,
            AiInferenceResponse aiResult,
            long submittedCount,
            long totalParticipantCount,
            Participant roundWinner) {

        Room room = round.getRoom();
        List<Participant> topScorers = findTopScorers(room.getId());

        // 단독 최고 승수면 게임 종료
        if (topScorers.size() == 1) {
            Participant finalWinner = topScorers.get(0);
            finalWinner.markWinner();
            room.finishGame();

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
     * 특정 라운드의 참가자 목록을 저장한다.
     */
    private void saveRoundParticipants(Round round, List<Participant> participants) {
        List<RoundParticipant> roundParticipants = participants.stream()
                .map(participant -> RoundParticipant.of(round, participant))
                .toList();

        roundParticipantRepository.saveAll(roundParticipants);
    }

    /**
     * 다음 일반 라운드를 생성하고 시작한다.
     */
    private Round createNextRound(Room room, int nextRoundNumber) {
        String keyword = keywordGenerator.generateKeyword();

        Round nextRound = Round.create(room, nextRoundNumber, keyword);
        nextRound.start();

        return roundRepository.save(nextRound);
    }

    /**
     * 결승 라운드를 생성하고 시작한다.
     */
    private Round createTieBreakerRound(Room room, int nextRoundNumber) {
        String keyword = keywordGenerator.generateKeyword();

        Round tieBreakerRound = Round.createTieBreaker(room, nextRoundNumber, keyword);
        tieBreakerRound.start();

        return roundRepository.save(tieBreakerRound);
    }

    /**
     * 현재 방에서 최고 승수를 가진 참가자 목록을 조회한다.
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
