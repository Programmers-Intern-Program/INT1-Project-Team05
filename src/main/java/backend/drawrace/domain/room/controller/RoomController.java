package backend.drawrace.domain.room.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import backend.drawrace.domain.room.dto.request.CreateRoomReq;
import backend.drawrace.domain.room.dto.request.JoinRoomReq;
import backend.drawrace.domain.room.dto.response.GetRoomListRes;
import backend.drawrace.domain.room.dto.response.RoomInfoRes;
import backend.drawrace.domain.room.service.RoomService;
import backend.drawrace.global.rsdata.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    // 방 생성
    @PostMapping
    public RsData<RoomInfoRes> createRoom(@Valid @RequestBody CreateRoomReq req) {
        RoomInfoRes response = roomService.createRoom(req, 1L);
        return new RsData<>("201-1", "방이 성공적으로 생성되었습니다.", response);
    }

    // 방 목록 조회
    @GetMapping
    public RsData<List<GetRoomListRes>> getRoomList() {
        List<GetRoomListRes> rooms = roomService.getRoomList();
        return new RsData<>("200-1", "방 목록을 성공적으로 조회했습니다.", rooms);
    }

    // 방 상세 조회
    @GetMapping("/{roomId}")
    public RsData<RoomInfoRes> getRoomDetail(@PathVariable Long roomId) {
        RoomInfoRes detail = roomService.getRoomDetail(roomId);
        return new RsData<>("200-2", "방 상세 정보를 성공적으로 조회했습니다.", detail);
    }

    // 방 입장
    @PostMapping("/{roomId}/join")
    public RsData<RoomInfoRes> joinRoom(@PathVariable Long roomId, @RequestBody(required = false) JoinRoomReq req) {
        String password = (req != null) ? req.password() : null;
        RoomInfoRes detail = roomService.joinRoom(roomId, 2L, password);
        return new RsData<>("200-3", "방에 입장했습니다.", detail);
    }

    // 게임 시작 요청
    /*   @PostMapping("/{roomId}/start")
      public RsData<RoundStartResponse> startGame(@PathVariable Long roomId) {
          roomService.startGame(roomId, 1L);
          return new RsData<>("200-5", "게임을 시작합니다.");
      }
    */
    // 방 퇴장
    @DeleteMapping("/{roomId}/leave")
    public RsData<Void> leaveRoom(@PathVariable Long roomId) {
        // 인증 로직 구현 전 임시 아이디
        roomService.leaveRoom(roomId, 1L);
        return new RsData<>("200-4", "방에서 성공적으로 퇴장했습니다.");
    }
}
