package backend.drawrace.domain.user.service;

import backend.drawrace.domain.user.dto.UserInfoResponse;

public interface UserService {

    /**
     * 유저 단건 조회
     * @param userId 조회할 유저 ID
     * @return 해당 유저 정보 dto
     */
    UserInfoResponse getUser(Long userId);

    /**
     * 회원 탈퇴 (Refresh Token 및 유저 삭제)
     * @param userId 탈퇴할 유저 ID
     */
    void deleteUser(Long userId);
}
