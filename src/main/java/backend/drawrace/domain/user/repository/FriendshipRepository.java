package backend.drawrace.domain.user.repository;

import backend.drawrace.domain.user.entity.Friendship;
import backend.drawrace.domain.user.entity.FriendshipStatus;
import backend.drawrace.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findByRequesterAndReceiver(User requester, User receiver);

    // 내가 받은 요청
    List<Friendship> findAllByReceiverAndStatus(User receiver, FriendshipStatus status);

    // 내가 보낸 요청
    List<Friendship> findAllByRequesterAndStatus(User requester, FriendshipStatus status);

    @Query("SELECT f FROM Friendship f " +
            "JOIN FETCH f.requester " +
            "JOIN FETCH f.receiver " +
            "WHERE (f.requester = :user OR f.receiver = :user) " +
            "AND f.status = 'ACCEPTED'")
    List<Friendship> findAllFriends(@Param("user") User user);
}