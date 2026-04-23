package backend.drawrace.domain.room.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.room.dto.request.CreateRoomReq;
import backend.drawrace.domain.room.dto.response.GetRoomListRes;
import backend.drawrace.domain.room.dto.response.RoomInfoRes;
import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomInfoRes createRoom(CreateRoomReq req, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        // Room 생성
        Room room = Room.builder()
                .title(req.title())
                .password(req.password())
                .hostId(userId)
                .totalRounds(req.totalRounds())
                .maxPlayers((short) 4)
                .build();

        roomRepository.save(room);

        // 방장을 Participant로 등록
        Participant host =
                Participant.builder().userId(user).room(room).isHost(true).build();

        participantRepository.save(host);
        room.getParticipants().add(host);

        return getRoomDetail(room.getId());
    }

    public List<GetRoomListRes> getRoomList() {
        return roomRepository.findAll().stream()
                .map(room -> {
                    String hostNickname = room.getParticipants().stream()
                            .filter(Participant::isHost)
                            .map(p -> p.getUserId().getNickname())
                            .findFirst()
                            .orElse("Unknown");

                    return GetRoomListRes.builder()
                            .roomId(room.getId())
                            .title(room.getTitle())
                            .curPlayers(room.getCurPlayers())
                            .maxPlayers(room.getMaxPlayers())
                            .isPlaying(room.isPlaying())
                            .hostNickname(hostNickname)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public RoomInfoRes getRoomDetail(Long roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

        List<RoomInfoRes.ParticipantDto> participantDtos = room.getParticipants().stream()
                .map(p -> new RoomInfoRes.ParticipantDto(
                        p.getUserId().getId(), p.getUserId().getNickname(), p.isHost()))
                .toList();

        return new RoomInfoRes(
                room.getId(),
                room.getTitle(),
                room.getCurPlayers(),
                room.getMaxPlayers(),
                room.getTotalRounds(),
                room.getHostId(),
                room.isPlaying(),
                participantDtos);
    }

    @Transactional
    public RoomInfoRes joinRoom(Long roomId, Long userId, String inputPassword) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

        // 비밀번호 체크 (방에 비밀번호가 걸려있는 경우만)
        if (room.getPassword() != null && !room.getPassword().isEmpty()) {
            if (!room.getPassword().equals(inputPassword)) {
                throw new ServiceException("400-4", "비밀번호가 일치하지 않습니다.");
            }
        }

        if (room.isPlaying()) {
            throw new ServiceException("400-2", "이미 게임이 시작된 방입니다.");
        }

        if (room.getCurPlayers() >= room.getMaxPlayers()) {
            throw new ServiceException("400-3", "방 인원이 초과되었습니다.");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(false).build();

        participantRepository.save(participant);
        room.addParticipant(participant);

        return getRoomDetail(roomId);
    }

    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));
        User user = userRepository.findById(userId).orElseThrow(() -> new ServiceException("404-1", "유저를 찾을 수 없습니다."));

        Participant participant = participantRepository
                .findByRoomAndUserId(room, user)
                .orElseThrow(() -> new ServiceException("404-3", "참여 정보를 찾을 수 없습니다."));

        // 방장이 나가는 경우 방장 위임 로직
        if (participant.isHost() && room.getCurPlayers() > 1) {
            Participant nextHost = room.getParticipants().stream()
                    .filter(p -> !p.equals(participant))
                    .findFirst()
                    .orElseThrow(() -> new ServiceException("500-2", "다음 방장을 찾을 수 없습니다."));

            nextHost.makeHost();
            room.changeHost(nextHost.getUserId().getId());
        }
        // 참여자 제거 및 인원 감소
        room.removeParticipant(participant);
        participantRepository.delete(participant);

        // 방에 아무도 없으면 방 삭제
        if (room.getCurPlayers() == 0) {
            roomRepository.delete(room);
        }
    }

    /*
    @Transactional
    public void startGame(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ServiceException("404-2", "방을 찾을 수 없습니다."));

        if (!room.getHostId().equals(userId)) {
            throw new ServiceException("403-2", "방장만 게임을 시작할 수 있습니다.");
        }

        if (room.getCurPlayers() < 2) {
            throw new ServiceException("400-5", "최소 2명의 플레이어가 필요합니다.");
        }

        room.startGame();
    }

     */
}
