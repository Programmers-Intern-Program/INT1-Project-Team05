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

    /**
     * Access Token 재발급
     * @param request 유효한 Refresh Token
     * @return 새로 발급된 Access Token과 Refresh Token
     */
    LoginResponse reissue(TokenRequest request);

    /**
     * 로그아웃 (Refresh Token 삭제)
     * @param userId 로그아웃할 유저 ID
     */
    void logout(Long userId);
}
