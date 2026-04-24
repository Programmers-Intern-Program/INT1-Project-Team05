package backend.drawrace.domain.round.service;

import java.util.List;

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
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 방입니다."));

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
     * - 로그인한 사용자 본인의 참가 정보인지 검증
     * - 중복 제출을 막고 제출 기록을 저장
     * - 아직 전원 제출 전이면 대기 응답
     * - 전원 제출 완료면 라운드 승자를 선정하고 다음 상태를 결정
     */
    @Transactional
    public SubmitDrawingResponse submitDrawing(Long roundId, Long userId, SubmitDrawingRequest request) {
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new ServiceException("404-2", "존재하지 않는 라운드입니다."));

        roundValidator.validateRoundInProgress(round);

        // 방 소속 참가자인지 검증
        Participant participant = getValidParticipant(round, request.getParticipantId());

        // 로그인한 사용자 본인의 참가 정보인지 검증
        roundValidator.validateParticipantOwner(participant, userId);

        // 이번 라운드 제출 대상인지 검증
        boolean canPlay =
                roundParticipantRepository.existsByRoundIdAndParticipantId(round.getId(), participant.getId());
        roundValidator.validateRoundParticipant(canPlay);

        // 이미 제출했는지 검증
        boolean alreadySubmitted =
                roundSubmissionRepository.existsByRoundIdAndParticipantId(round.getId(), participant.getId());
        roundValidator.validateNotSubmitted(alreadySubmitted);

        // AI 판독
        AiInferenceResponse aiResult = aiInferenceService.infer(request.getImageData(), round.getKeyword());

        // 제출 기록 저장
        RoundSubmission submission = RoundSubmission.create(
                round, participant, request.getImageData(), aiResult.getAiAnswer(), aiResult.getScore());
        roundSubmissionRepository.save(submission);

        // 현재 제출 수 확인
        long submittedCount = roundSubmissionRepository.countByRoundId(round.getId());
        long totalParticipantCount = roundParticipantRepository.countByRoundId(round.getId());

        // 아직 전원 제출 전이면 대기 응답
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

        // 전원 제출 완료면 라운드 종료 처리
        return handleRoundCompletion(round, aiResult, submittedCount, totalParticipantCount);
    }

    /**
     * 현재 진행 중인 라운드 조회
     */
    @Transactional(readOnly = true)
    public CurrentRoundResponse getCurrentRound(Long roomId, Long userId) {
        validateRoomMember(roomId, userId);

        Round currentRound = roundRepository.findByRoomIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new ServiceException("404-3", "현재 진행 중인 라운드가 없습니다."));

        List<RoundParticipantResponse> participants =
                roundParticipantRepository.findByRoundId(currentRound.getId()).stream()
                        .map(roundParticipant -> RoundParticipantResponse.from(roundParticipant.getParticipant()))
                        .toList();

        return CurrentRoundResponse.of(currentRound, participants);
    }

    private void validateRoomMember(Long roomId, Long userId) {
        boolean isRoomMember = participantRepository.findByRoomId(roomId).stream()
                .anyMatch(participant -> participant.getUserId().getId().equals(userId));

        if (!isRoomMember) {
            throw new ServiceException("403-4", "해당 방 참가자만 현재 라운드를 조회할 수 있습니다.");
        }
    }

    /**
     * 현재 라운드의 방에 속한 참가자를 조회한다.
     */
    private Participant getValidParticipant(Round round, Long participantId) {
        return participantRepository.findByIdAndRoomId(participantId, round.getRoom().getId())
                .orElseThrow(() -> new ServiceException("404-4", "해당 방에 속한 참가자가 아닙니다."));
    }

    /**
     * 전원 제출 완료 시 이번 라운드의 승자를 선정하고 라운드를 종료한다.
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
     */
    private SubmitDrawingResponse handleAfterRoundFinished(
            Round round,
            AiInferenceResponse aiResult,
            long submittedCount,
            long totalParticipantCount,
            Participant roundWinner) {

        Room room = round.getRoom();

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

        return handleLastNormalRound(round, aiResult, submittedCount, totalParticipantCount, roundWinner);
    }

    /**
     * 마지막 일반 라운드 처리
     */
    private SubmitDrawingResponse handleLastNormalRound(
            Round round,
            AiInferenceResponse aiResult,
            long submittedCount,
            long totalParticipantCount,
            Participant roundWinner) {

        Room room = round.getRoom();
        List<Participant> topScorers = findTopScorers(room.getId());

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