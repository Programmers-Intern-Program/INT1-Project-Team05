package backend.drawrace.domain.user.service;

import backend.drawrace.domain.user.dto.FriendInfoResponse;
import backend.drawrace.domain.user.dto.FriendRequestResponse;
import backend.drawrace.domain.user.entity.Friendship;
import backend.drawrace.domain.user.entity.FriendshipStatus;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserService userService;

    @Override
    @Transactional
    public void sendFriendRequest(Long requesterId, Long receiverId) {

        // 자신에게는 못 보냄
        if (requesterId.equals(receiverId)) {
            throw new IllegalArgumentException("자신에게 친구 요청을 보낼 수 없습니다.");
        }

        User requester = userService.findById(requesterId);
        User receiver = userService.findById(receiverId);

        // 중복 요청 방지
        friendshipRepository.findByRequesterAndReceiver(requester, receiver)
                .ifPresent(f -> {
                    throw new IllegalStateException("이미 처리 중이거나 완료된 친구 요청이 있습니다.");
                });

        // 친구 요청 저장 (기본 상태는 PENDING)
        Friendship friendship = Friendship.builder()
                .requester(requester)
                .receiver(receiver)
                .status(FriendshipStatus.PENDING)
                .build();

        friendshipRepository.save(friendship);
    }

    @Override
    @Transactional
    public void acceptFriendRequest(Long receiverId, Long friendshipId) {
        // 요청 존재 확인
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 친구 요청입니다."));

        // 수신자 일치 여부 확인
        if (!friendship.getReceiver().getId().equals(receiverId)) {
            throw new IllegalStateException("본인에게 온 요청만 수락할 수 있습니다.");
        }

        // 상태가 PENDING인지 확인
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }

        // 상태 업데이트 -> 수락
        friendship.updateStatus(FriendshipStatus.ACCEPTED);
    }

    @Override
    @Transactional
    public void rejectFriendRequest(Long receiverId, Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 친구 요청입니다."));

        if (!friendship.getReceiver().getId().equals(receiverId)) {
            throw new IllegalStateException("권한이 없습니다.");
        }

        // 거절 시 데이터 삭제
        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getReceivedRequests(Long receiverId) {
        User receiver = userService.findById(receiverId);
        return friendshipRepository.findAllByReceiverAndStatus(receiver, FriendshipStatus.PENDING)
                .stream()
                .map(f -> FriendRequestResponse.from(f, f.getRequester()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getSentRequests(Long requesterId) {
        User requester = userService.findById(requesterId);
        return friendshipRepository.findAllByRequesterAndStatus(requester, FriendshipStatus.PENDING)
                .stream()
                .map(f -> FriendRequestResponse.from(f, f.getReceiver()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendInfoResponse> getFriendList(Long userId) {
        User user = userService.findById(userId);
        List<Friendship> friendships = friendshipRepository.findAllFriends(user);

        return friendships.stream()
                .map(f -> {
                    // 둘 중 내가 아닌 사람이 친구임
                    User friend = f.getRequester().getId().equals(userId)
                            ? f.getReceiver()
                            : f.getRequester();
                    return FriendInfoResponse.from(friend);
                })
                .toList();
    }

}
