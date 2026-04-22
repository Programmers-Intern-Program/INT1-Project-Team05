package backend.drawrace.domain.round.validator;

import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RoundValidator {

    public void validateRoomNotPlaying(Room room) {
        if (room.isPlaying()) {
            throw new IllegalStateException("이미 게임이 진행 중인 방입니다. roomId=" + room.getId());
        }
    }

    public void validateParticipantCount(long participantCount, Long roomId) {
        if (participantCount < 2) {
            throw new IllegalStateException("게임 시작은 최소 2명 이상부터 가능합니다. roomId=" + roomId);
        }
    }

    public void validateNoActiveRound(Optional<Round> activeRound) {
        activeRound.ifPresent(round -> {
            throw new IllegalStateException("이미 진행 중인 라운드가 존재합니다. roundId=" + round.getId());
        });
    }

    public void validateStartGame(Room room, long participantCount, Optional<Round> activeRound) {
        validateRoomNotPlaying(room);
        validateParticipantCount(participantCount, room.getId());
        validateNoActiveRound(activeRound);
    }

    public void validateRoundInProgress(Round round) {
        if (round.getStatus() != RoundStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 라운드가 아닙니다. roundId=" + round.getId());
        }
    }
}