package backend.drawrace.domain.user.dto;

import backend.drawrace.domain.user.entity.User;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {
    private Long id;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private int totalGameCount;
    private int winGameCount;

    public static UserInfoResponse from(User user) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .totalGameCount(user.getStats().getTotalGameCount())
                .winGameCount(user.getStats().getWinGameCount())
                .build();
    }
}
