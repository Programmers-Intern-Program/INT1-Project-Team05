package backend.drawrace.domain.user.service;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.LoginRequest;

public interface AuthService{

    /**
     * 회원가입 (유저 생성)
     * @param dto 가입 정보 (email, password, nickname)
     * @return 생성된 유저의 ID
     */
    Long signup(CreateUserRequest dto);

    /**
     * 로그인
     * @param dto 로그인 정보 (email, password)
     * @return 생성된 유저의 토큰
     */
    public String login(LoginRequest dto);
}
