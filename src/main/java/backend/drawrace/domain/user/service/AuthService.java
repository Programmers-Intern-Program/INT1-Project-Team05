package backend.drawrace.domain.user.service;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.LoginRequest;
import backend.drawrace.domain.user.dto.LoginResponse;
import backend.drawrace.domain.user.dto.TokenRequest;

public interface AuthService {

    /**
     * 회원가입 (유저 생성)
     * @param dto 가입 정보 (email, password, nickname)
     * @return 생성된 유저의 ID
     */
    Long signup(CreateUserRequest dto);

    /**
     * 로그인
     * @param dto 로그인 정보 (email, password)
     * @return Access Token과 Refresh Token
     */
    LoginResponse login(LoginRequest dto);

    LoginResponse reissue(TokenRequest request);

    void logout(Long userId);
}
