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
import backend.drawrace.domain.user.repository.UserRepository;

@SpringBootTest
@Transactional // 테스트 후 롤백되어 DB에 영향을 주지 않음
class UserServiceTest {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("회원가입_성공")
    void signup_success() {
        // given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("test@example.com")
                .password("password123")
                .nickname("테스터")
                .build();

        // when
        Long savedId = userService.signup(request);

        // then
        assertThat(savedId).isNotNull();
        assertThat(userRepository.findById(savedId)).isPresent();
    }

    @Test
    @DisplayName("중복_이메일_가입_실패")
    void signup_fail_duplicate_email() {
        // given
        CreateUserRequest request1 = CreateUserRequest.builder()
                .email("same@example.com")
                .password("pw1")
                .nickname("nick1")
                .build();
        userService.signup(request1);

        CreateUserRequest request2 = CreateUserRequest.builder()
                .email("same@example.com")
                .password("pw2")
                .nickname("nick2")
                .build();

        // when & then
        assertThatThrownBy(() -> userService.signup(request2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("단건_조회_성공")
    void findOne_success() {
        // given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("find@example.com")
                .password("pw")
                .nickname("조회대상")
                .build();
        Long savedId = userService.signup(request);

        // when
        UserInfoResponse response = userService.getUser(savedId);

        // then
        assertThat(response.getId()).isEqualTo(savedId);
        assertThat(response.getEmail()).isEqualTo("find@example.com");
        assertThat(response.getNickname()).isEqualTo("조회대상");
    }
}
