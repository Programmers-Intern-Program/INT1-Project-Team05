package backend.drawrace.domain.room.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import backend.drawrace.domain.room.dto.request.CreateRoomReq;
import backend.drawrace.domain.room.dto.response.RoomInfoRes;
import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.user.entity.User;
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

    @InjectMocks
    private RoomService roomService;

    @Test
    @DisplayName("방 생성 성공 - 인원은 4명으로 고정된다")
    void createRoom_success() throws Exception {
        Long userId = 1L;
        CreateRoomReq req = new CreateRoomReq("테스트 방", (short) 4, (short) 3, "1234");
        User user = User.builder().email("test@test.com").nickname("채은").build();

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

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(userRepository.findById(hostId)).willReturn(Optional.of(hostUser));
        given(participantRepository.findByRoomAndUserId(room, hostUser)).willReturn(Optional.of(hostPart));

        // when
        roomService.leaveRoom(roomId, hostId);

        // then
        assertThat(room.getHostId()).isEqualTo(nextHostId);
        assertThat(nextPart.isHost()).isTrue();
        verify(participantRepository).delete(hostPart);
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
        User user = User.builder().nickname(nickname).build();
        setField(user, "id", id);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
