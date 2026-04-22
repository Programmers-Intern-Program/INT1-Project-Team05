package backend.drawrace.domain.round.validator;

import java.util.Optional;

import org.springframework.stereotype.Component;

import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundStatus;

@Component
public class RoundValidator {

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

    public void validateRoundParticipant(boolean canPlay, Long participantId) {
        if (!canPlay) {
            throw new IllegalStateException("이번 라운드 참가 대상이 아닙니다. participantId=" + participantId);
        }
    }

    private void validateRoomNotPlaying(Room room) {
        if (room.isPlaying()) {
            throw new IllegalStateException("이미 게임이 진행 중인 방입니다. roomId=" + room.getId());
        }
    }

    private void validateParticipantCount(long participantCount, Long roomId) {
        if (participantCount < 2) {
            throw new IllegalStateException("게임 시작은 최소 2명 이상부터 가능합니다. roomId=" + roomId);
        }
    }

    private void validateNoActiveRound(Optional<Round> activeRound) {
        activeRound.ifPresent(round -> {
            throw new IllegalStateException("이미 진행 중인 라운드가 존재합니다. roundId=" + round.getId());
        });
    }
}
