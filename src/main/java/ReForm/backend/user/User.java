package ReForm.backend.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder

@Table(name = "user")
public class User {

    @Id
    @Column(name = "user_id")
    private String userId; // PK (VARCHAR). 외부 시스템 연동을 고려해 문자열 유지

    @Column(name = "email")
    private String email;

	@Column(name = "social_id")
	private String socialId; // 소셜 로그인 고유 식별자

    @Column(name = "user_name")
    private String userName;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "password")
    private String password;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "address")
    private String address;

	@Column(name = "phone")
	private String phoneNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // JPA에서 enum 타입 필드를 데이터베이스 컬럼과 매핑할 때 사용
    @Enumerated(EnumType.STRING) // 사용자 관리자 구분
    @Column(name = "role")
    private Role role;

    @Enumerated(EnumType.STRING) // 소셜 로그인 타입 구분
    @Column(name = "provider")
    private SocialType socialType;

    // 프로필 이미지 URL (기본값: /images/default-profile.png)
    @Column(name = "profile_image_url")
    private String profileImageUrl;

    public void authorizeUser() { // 사용자 역할을 USER로 할당
        this.role = Role.USER;
    }

    public void updateRefreshToken(String newRefreshToken) { // 리프레시 토큰 업데이트
        this.refreshToken = newRefreshToken;
    }
}

