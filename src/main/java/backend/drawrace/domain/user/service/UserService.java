package backend.drawrace.domain.user.service;

import backend.drawrace.domain.user.dto.CreateUserRequest;
import backend.drawrace.domain.user.dto.UserInfoResponse;

public interface UserService {

    /**
     * 회원가입 (유저 생성)
     * @param dto 가입 정보 (email, password, nickname)
     * @return 생성된 유저의 ID
     */
    Long signup(CreateUserRequest dto);

    /**
     * 유저 단건 조회
     * @param userId 찾고자 하는 유저 ID
     * @return 해당 유저 정보 dto
     */
    UserInfoResponse getUser(Long userId);
}
