package backend.drawrace.domain.user.repository;

import backend.drawrace.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 중복 체크용
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);

    // 단건 조회용
    Optional<User> findByNickname(String nickname);
}