package backend.drawrace.domain.user.service;

import backend.drawrace.domain.user.dto.UserInfoResponse;

public interface UserService {

    /**
     * 유저 단건 조회
     * @param userId 찾고자 하는 유저 ID
     * @return 해당 유저 정보 dto
     */
    UserInfoResponse getUser(Long userId);
}
