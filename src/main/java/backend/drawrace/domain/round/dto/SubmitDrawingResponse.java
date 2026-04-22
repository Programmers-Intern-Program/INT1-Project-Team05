package backend.drawrace.domain.round.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmitDrawingResponse {

    private Long roundId;
    private String aiAnswer;
    private boolean correct;
    private String keyword;
    private int roundWinCount;
}