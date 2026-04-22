package backend.drawrace.domain.round.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmitDrawingResponse {

    private Long roundId;
    private String aiAnswer;
    private double score;

    private int submittedCount;
    private int totalParticipantCount;

    private boolean roundFinished;
    private boolean gameFinished;
    private boolean tieBreakerStarted;

    private Long roundWinnerParticipantId;

    private Long nextRoundId;
    private Integer nextRoundNumber;
    private boolean nextRoundTieBreaker;

    private Long finalWinnerParticipantId;
}