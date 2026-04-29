package backend.drawrace.domain.room.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.room.dto.request.CreateRoomReq;
import backend.drawrace.domain.room.dto.response.RoomInfoRes;
import backend.drawrace.domain.room.dto.response.RoomUpdateResponse;
import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.entity.UserStats;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RankingService rankingService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomService roomService;

    @Test
    @DisplayName("방 생성 성공 - 인원은 4명으로 고정된다")
    void createRoom_success() throws Exception {
        Long userId = 1L;
        CreateRoomReq req = new CreateRoomReq("테스트 방", (short) 4, (short) 3, "1234");
        User user = User.builder()
                .email("test@test.com")
                .nickname("유저A")
                .isAi(false)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(roomRepository.save(any(Room.class))).willAnswer(inv -> {
            Room room = inv.getArgument(0);
            setField(room, "id", 100L);
            return room;
        });
        given(roomRepository.findById(100L)).willReturn(Optional.of(createRoom(100L, "테스트 방", userId)));

        RoomInfoRes res = roomService.createRoom(req, userId);

        assertThat(res.title()).isEqualTo("테스트 방");
        assertThat(res.maxPlayers()).isEqualTo((short) 4);
        assertThat(res.hostId()).isEqualTo(userId);
        verify(participantRepository).save(any(Participant.class));
    }

    @Test
    @DisplayName("방 입장 실패 - 비밀번호가 틀리면 예외가 발생한다")
    void joinRoom_fail_password() throws Exception {

        Long roomId = 100L;
        Room room = Room.builder()
                .title("비번방")
                .password("1234")
                .maxPlayers((short) 4)
                .build();
        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.joinRoom(roomId, 2L, "wrong_pw"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-4");
    }

    @Test
    @DisplayName("입장 시 동시성 충돌이 발생하면 최대 3번까지 재시도한다")
    void shouldRetryWhenOptimisticLockConflictOccurs() throws Exception {
        Long roomId = 1L;
        Long userId = 1L;

        Room room = createRoom(roomId, "테스트방", 2L);
        User user = createUser(userId, "채은");

        // 첫 번째, 두 번째 시도는 충돌 발생, 세 번째에 성공하도록 설정
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // save할 때 낙관적 락 예외를 두 번 던지게 함
        doThrow(ObjectOptimisticLockingFailureException.class) // 1회차 실패
                .doThrow(ObjectOptimisticLockingFailureException.class) // 2회차 실패
                .doAnswer(invocation -> invocation.getArgument(0)) // 3회차 성공
                .when(participantRepository)
                .save(any());

        roomService.joinRoom(roomId, userId, null);

        // findById가 총 3번(최초 + 재시도 2번) 호출되었는지 확인
        verify(roomRepository, times(3)).findById(roomId);
        verify(participantRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("유저가 방을 나갈 때 실시간 업데이트 응답을 반환한다")
    void leaveRoom_ReturnRoomUpdateResponse() {
        Long userId = 1L;
        User user = mock(User.class);
        given(user.getNickname()).willReturn("유저A");

        Room room = mock(Room.class);
        given(room.getId()).willReturn(10L);
        doReturn((short) 2).when(room).getCurPlayers(); // 남은 인원이 있는 경우

        Participant participant = mock(Participant.class);
        given(participant.getRoom()).willReturn(room);
        given(participant.isHost()).willReturn(false);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(participantRepository.findByUserId(user)).willReturn(Optional.of(participant));
        given(participant.getUserId()).willReturn(user);
        given(room.getParticipants()).willReturn(List.of(participant));

        RoomUpdateResponse response = roomService.leaveRoom(userId);

        assertThat(response).isNotNull();
        assertThat(response.getRoomId()).isEqualTo(10L);
        assertThat(response.getType()).isEqualTo("USER_LEAVE");
        assertThat(response.getMessage()).contains("유저A님이 퇴장하셨습니다.");

        verify(participantRepository, times(1)).delete(participant);
    }

    @Test
    @DisplayName("게임 종료 성공 - 우승자 마킹 및 스탯이 업데이트된다")
    void finishGame_success() throws Exception {

        Long roomId = 100L;
        Long winnerId = 1L;
        Room room = createRoom(roomId, "게임방", winnerId);
        setField(room, "isPlaying", true);

        User winnerUser = createUser(winnerId, "우승자");
        UserStats stats = UserStats.builder().user(winnerUser).build();
        setField(winnerUser, "stats", stats);

        Participant participant = Participant.builder()
                .userId(winnerUser)
                .room(room)
                .roundWinCount(3)
                .build();

        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        given(rankingService.getRankingList(roomId)).willReturn(Set.of(tuple));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant));

        roomService.finishGame(roomId);

        assertThat(room.isPlaying()).isFalse();
        assertThat(participant.isWinner()).isTrue();
        assertThat(stats.getTotalGameCount()).isEqualTo(1);
        assertThat(stats.getWinGameCount()).isEqualTo(1);
        verify(rankingService).clearRanking(roomId);
    }

    @Test
    @DisplayName("방 퇴장 성공 - 방장이 나가면 다음 사람에게 위임된다")
    void leaveRoom_hostDelegation() throws Exception {
        // given
        Long roomId = 100L;
        Long hostId = 1L;
        Long nextHostId = 2L;

        Room room = createRoom(roomId, "위임테스트", hostId);
        User hostUser = createUser(hostId, "방장");
        User nextUser = createUser(nextHostId, "다음방장");

        Participant hostPart =
                Participant.builder().userId(hostUser).room(room).isHost(true).build();
        Participant nextPart =
                Participant.builder().userId(nextUser).room(room).isHost(false).build();

        // 방에 두 명 참여 중
        room.addParticipant(hostPart);
        room.addParticipant(nextPart);

        given(userRepository.findById(hostId)).willReturn(Optional.of(hostUser));
        given(participantRepository.findByUserId(hostUser)).willReturn(Optional.of(hostPart));

        roomService.leaveRoom(hostId);

        assertThat(room.getHostId()).isEqualTo(nextHostId);
        assertThat(nextPart.isHost()).isTrue();
        verify(participantRepository).delete(hostPart);
    }

    @Test
    @DisplayName("유저 퇴장 시 퇴장 알림과 방장 위임 알림이 채팅창에 전달된다")
    void leaveRoom_ShouldSendSystemNotice() {
        // 1. Given: 테스트에 필요한 가짜 객체(Mock) 설정
        Long userId = 1L;
        Long roomId = 10L;

        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(user.getNickname()).thenReturn("유저A");

        Room room = mock(Room.class);
        lenient().when(room.getId()).thenReturn(roomId);
        lenient().when(room.getCurPlayers()).thenReturn((short) 2); // 2명 있는 방

        Participant participant = mock(Participant.class);
        lenient().when(participant.getRoom()).thenReturn(room);
        lenient().when(participant.isHost()).thenReturn(true); // 방장이 나가는 상황 가정
        lenient().when(participant.getUserId()).thenReturn(user);

        // 다음 방장이 될 사람 설정
        Participant nextHost = mock(Participant.class);
        User nextUser = mock(User.class);
        lenient().when(nextUser.getNickname()).thenReturn("다음방장");
        lenient().when(nextHost.getUserId()).thenReturn(nextUser);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(participantRepository.findByUserId(user)).willReturn(Optional.of(participant));

        lenient().when(room.getParticipants()).thenReturn(List.of(participant, nextHost));

        roomService.leaveRoom(userId);

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(
                        eq("/sub/rooms/" + roomId + "/chat"),
                        argThat((ChatMessageDto dto) -> dto.getType() == ChatMessageDto.MessageType.NOTICE
                                && dto.getMessage().contains("유저A님이 퇴장하셨습니다.")));

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(
                        eq("/sub/rooms/" + roomId + "/chat"),
                        argThat((ChatMessageDto dto) -> dto.getType() == ChatMessageDto.MessageType.NOTICE
                                && dto.getMessage().contains("방장이 다음방장님으로 변경되었습니다.")));
    }

    private Room createRoom(Long id, String title, Long hostId) throws Exception {
        Room room = Room.builder()
                .title(title)
                .hostId(hostId)
                .maxPlayers((short) 4)
                .curPlayers((short) 1)
                .participants(new ArrayList<>())
                .build();
        setField(room, "id", id);
        return room;
    }

    private User createUser(Long id, String nickname) throws Exception {
        User user = User.builder().nickname(nickname).isAi(false).build();
        setField(user, "id", id);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
