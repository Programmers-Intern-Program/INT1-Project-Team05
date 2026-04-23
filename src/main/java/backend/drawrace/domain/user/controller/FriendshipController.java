package backend.drawrace.domain.user.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.drawrace.domain.user.dto.FriendInfoResponse;
import backend.drawrace.domain.user.dto.FriendRequestResponse;
import backend.drawrace.domain.user.service.FriendshipService;
import backend.drawrace.global.rsdata.RsData;
import backend.drawrace.global.security.SecurityUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/friendship")
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/request/{receiverId}")
    public RsData<Void> sendFriendRequest(
            @AuthenticationPrincipal SecurityUser securityUser, @PathVariable Long receiverId) {
        friendshipService.sendFriendRequest(securityUser.getUserId(), receiverId);
        return new RsData<>("201-1", "친구 요청을 보냈습니다.");
    }

    @PostMapping("/{friendshipId}/accept")
    public RsData<Void> acceptFriendRequest(
            @AuthenticationPrincipal SecurityUser securityUser, @PathVariable Long friendshipId) {
        friendshipService.acceptFriendRequest(securityUser.getUserId(), friendshipId);
        return new RsData<>("200-1", "친구 요청을 수락했습니다.");
    }

    @DeleteMapping("/{friendshipId}/reject")
    public RsData<Void> rejectFriendRequest(
            @AuthenticationPrincipal SecurityUser securityUser, @PathVariable Long friendshipId) {
        friendshipService.rejectFriendRequest(securityUser.getUserId(), friendshipId);
        return new RsData<>("200-2", "친구 요청을 거절했습니다.");
    }

    @GetMapping("/requests/received")
    public RsData<List<FriendRequestResponse>> getReceivedRequests(@AuthenticationPrincipal SecurityUser securityUser) {
        List<FriendRequestResponse> requests = friendshipService.getReceivedRequests(securityUser.getUserId());
        return new RsData<>("200-3", "받은 친구 요청 목록을 조회했습니다.", requests);
    }

    @GetMapping("/requests/sent")
    public RsData<List<FriendRequestResponse>> getSentRequests(@AuthenticationPrincipal SecurityUser securityUser) {
        List<FriendRequestResponse> requests = friendshipService.getSentRequests(securityUser.getUserId());
        return new RsData<>("200-4", "보낸 친구 요청 목록을 조회했습니다.", requests);
    }

    @GetMapping
    public RsData<List<FriendInfoResponse>> getFriendList(@AuthenticationPrincipal SecurityUser securityUser) {
        List<FriendInfoResponse> friends = friendshipService.getFriendList(securityUser.getUserId());
        return new RsData<>("200-5", "친구 목록을 조회했습니다.", friends);
    }
}
