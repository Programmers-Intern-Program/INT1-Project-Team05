package backend.drawrace.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.UserInfoResponse;
import backend.drawrace.global.exception.ServiceException;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    UserService userService;

    @Autowired
    AuthService authService;

    private Long createTestUser() {
        return authService.signup(new CreateUserRequest("test@example.com", "password123", "테스터"));
    }

    @Test
    @DisplayName("유저_단건_조회_성공")
    void getUser_success() {
        Long savedId = createTestUser();

        UserInfoResponse response = userService.getUser(savedId);

        assertThat(response.id()).isEqualTo(savedId);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("유저_단건_조회_실패_존재하지_않는_ID")
    void getUser_fail_not_found() {
        assertThatThrownBy(() -> userService.getUser(999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("회원_탈퇴_성공")
    void deleteUser_success() {
        Long savedId = createTestUser();

        userService.deleteUser(savedId);

        assertThatThrownBy(() -> userService.getUser(savedId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }

    @Test
    @DisplayName("회원_탈퇴_실패_존재하지_않는_ID")
    void deleteUser_fail_not_found() {
        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "404-1");
    }
}
