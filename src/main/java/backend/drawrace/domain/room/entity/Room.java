package backend.drawrace.domain.room.entity;

import backend.drawrace.domain.user.entity.User;
import backend.drawrace.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    private String password;

    @Column(nullable = false)
    private Long hostId;

    private short totalRounds;
    private short maxPlayers;
    private short curPlayers;

    private boolean isPlaying = false;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participant> participants = new ArrayList<>();
}