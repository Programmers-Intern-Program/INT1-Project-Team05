package backend.drawrace.domain.round.dto;

import backend.drawrace.domain.room.entity.Participant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoundParticipantResponse {

    private Long participantId;
    private int roundWinCount;
    private boolean isHost;
    private boolean isWinner;

    public static RoundParticipantResponse from(Participant participant) {
        return RoundParticipantResponse.builder()
                .participantId(participant.getId())
                .roundWinCount(participant.getRoundWinCount())
                .isHost(participant.isHost())
                .isWinner(participant.isWinner())
                .build();
    }
}