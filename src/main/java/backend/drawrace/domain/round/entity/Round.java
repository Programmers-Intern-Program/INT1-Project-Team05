package backend.drawrace.domain.round.entity;

import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Round extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "round_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private int roundNumber;

    @Column(nullable = false)
    private String keyword;

    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    private RoundStatus status; // READY, IN_PROGRESS, FINISHED

    private LocalDateTime startedAt;
}