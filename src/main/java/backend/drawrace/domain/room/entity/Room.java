package backend.drawrace.domain.room.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import backend.drawrace.global.entity.BaseEntity;

import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    private String password;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "total_rounds", nullable = false)
    private short totalRounds;

    @Column(name = "max_players", nullable = false)
    private short maxPlayers;

    @Column(name = "cur_players", nullable = false)
    @Builder.Default
    private short curPlayers = 1; // 방 생성 시 방장은 자동으로 포함되므로 1부터 시작

    @Column(name = "is_playing", nullable = false)
    @Builder.Default
    private boolean isPlaying = false;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    public void addParticipant(Participant participant) {
        this.participants.add(participant);
        this.curPlayers++;
    }

    public void removeParticipant(Participant participant) {
        this.participants.remove(participant);
        this.curPlayers--;
    }

    public void changeHost(Long newHostId) {
        this.hostId = newHostId;
    }

    public void startGame() {
        this.isPlaying = true;
    }

    public void finishGame() {
        this.isPlaying = false;
    }
}
