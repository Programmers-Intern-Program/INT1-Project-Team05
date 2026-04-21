package backend.drawrace.domain.user.service;

import backend.drawrace.domain.user.dto.CreateUserRequest;

public interface UserService {

    /**
     * 회원가입 (유저 생성)
     * @param dto 가입 정보 (email, password, nickname)
     * @return 생성된 유저의 ID
     */
    Long signup(CreateUserRequest dto);

}
