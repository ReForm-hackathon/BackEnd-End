package ReForm.backend.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
// 일반 사용자가 관리자를 나눠서 구분
public enum Role {
    USER("ROLE_USER"), ADMIN("ROLE_ADMIN");

    // key 필드를 추가하고 "ROLE_"를 붙인 이유는 스프링 시큐리티 권한 코드에 항상 "ROLE_" 접두사가 앞에 붙어야하기 때문
    private final String key;
}
