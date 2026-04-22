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
        return authService.signup(CreateUserRequest.builder()
                .email("test@example.com")
                .password("password123")
                .nickname("테스터")
                .build());
    }

    @Test
    @DisplayName("유저_단건_조회_성공")
    void getUser_success() {
        Long savedId = createTestUser();

        UserInfoResponse response = userService.getUser(savedId);

        assertThat(response.getId()).isEqualTo(savedId);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getNickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("유저_단건_조회_실패_존재하지_않는_ID")
    void getUser_fail_not_found() {
        assertThatThrownBy(() -> userService.getUser(999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("statusCode", 404);
    }
}
