package ReForm.backend.user.socialLogin.userinfo;

import ReForm.backend.user.Role;
import ReForm.backend.user.SocialType;
import ReForm.backend.user.User;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

@Getter
/**
 * 구글 , 카카오, 네이버 각 소셜 로그인마다 데이터를 전송해서 받아오는게 다름
 * 소셜별로 데이터 받는 데이터를 분기를 나눠서 처리함
 */
public class OAuthDTO {

    private String nameAttributeKey; // 소셜 로그인별 사용자를 식별하는 고유키 ex) 구글 : sub, 카카오, 네이버: id (PK 같은 역할)
    private OAuth2UserInfo oAuth2UserInfo; // 소셜 로그인 진행시 사용자 정보 ex) 닉네임, 프로필 사진 등

    @Builder // 생성자 생성
    public OAuthDTO(String nameAttributeKey, OAuth2UserInfo oAuth2UserInfo) {
        this.nameAttributeKey = nameAttributeKey;
        this.oAuth2UserInfo = oAuth2UserInfo;
    }

    // 구글, 카카오, 네이버 각 소셜 로그인별로 맞는 메서드를 호출해서 객체를 반환하는 로직
    /**
     * of : DTO 객체를 생성할 때 소셜 타입 별로 초기화
     * Map 사용 이유 : 소셜 제공자가 사용자 정보를 JSON 형태로 제공 자바에서는 Map<String, Object> 형태로 받음
     * attributes : 소셜 제공자가 제공해주는 사용자 정보 전체
     */

    public static OAuthDTO of(SocialType socialType,
                              String nameAttributeKey, Map<String, Object> attributes) {

        if(socialType == SocialType.KAKAO) { // 카카오 로그인시 DTO에 전달되는 객체 리턴
            return ofKakao(nameAttributeKey, attributes);
        }
        else if(socialType == SocialType.NAVER) {  // 네이버 로그인시 DTO에 전달되는 객체 리턴
            return ofNaver(nameAttributeKey, attributes);
        }
        else if(socialType == SocialType.GOOGLE) { // 구글 로그인시 DTO에 전달되는 객체 리턴
            return ofGoogle(nameAttributeKey, attributes);
        }
        else {
            throw new IllegalArgumentException("지원하지 않는 소셜로그인 방식입니다.");
        }
    }

    // 각 소셜별로 회원의 식별값 (sub, id)와 attributes를 저장 후 빌드시키는 로직

    private static OAuthDTO ofKakao(String nameAttributeKey, Map<String, Object> attributes) {
        return OAuthDTO.builder()
                .nameAttributeKey(nameAttributeKey)
                .oAuth2UserInfo(new KakaoOAuth2UserInfo(attributes))
                .build();
    }

    private static OAuthDTO ofNaver(String nameAttributeKey, Map<String, Object> attributes) {
        return OAuthDTO.builder()
                .nameAttributeKey(nameAttributeKey)
                .oAuth2UserInfo(new NaverOAuth2UserInfo(attributes))
                .build();
    }

    private static OAuthDTO ofGoogle(String nameAttributeKey, Map<String, Object> attributes) {
        return OAuthDTO.builder()
                .nameAttributeKey(nameAttributeKey)
                .oAuth2UserInfo(new GoogleOAuth2UserInfo(attributes))
                .build();
    }

    // of 메서드 통해서 OAuthDTO 객체 생성 OAuth2UserInfo에서 소셜 타입별로 attributes 빌드
    public User toEntity(SocialType socialType, OAuth2UserInfo oAuth2UserInfo) {
        String providerUserId = oAuth2UserInfo.getId();
        String generatedUserId = (socialType != null && providerUserId != null)
                ? socialType.name().toLowerCase() + "_" + providerUserId
                : UUID.randomUUID().toString();

        return User.builder()
                .userId(generatedUserId)
                .socialType(socialType)
                .socialId(oAuth2UserInfo.getId())
                .userName(oAuth2UserInfo.getName())
                .email(oAuth2UserInfo.getEmail())
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
