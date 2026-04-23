package backend.drawrace.domain.user.service;

import backend.drawrace.domain.user.dto.UserInfoResponse;

public interface UserService {

    UserInfoResponse getUser(Long userId);

    void deleteUser(Long userId);
}
