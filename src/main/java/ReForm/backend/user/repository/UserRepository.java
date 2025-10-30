package ReForm.backend.user.repository;

import ReForm.backend.user.SocialType;
import ReForm.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> { // PK: user_id(VARCHAR)

    // 로그인 후 추후 회원이 서비스를 이용할 때 이 값들을 찾아서 인증되면 서비스 사용가능하게 로직 진행
    Optional<User> findByEmail(String email); // 사용자 이메일을 조회해서 값을 찾음 (로그인 후 회원가입시)
    Optional<User> findFirstByEmailOrderByCreatedAtDesc(String email); // 이메일 중복 시 최신 레코드 우선
    Optional<User> findByNickname(String nickname); // 사용자 닉네임을 조회해서 값을 찾음 (추가정보 입력시)
    Optional<User> findByRefreshToken(String refreshToken); // 리프레시 토큰 값을 조회해서 값을 찾음

    Optional<User> findByEmailAndSocialType(String email, SocialType socialType); // RT DB 저장할 때

    // 사용자가 로그인한 소셜 타입과 소셜 ID를 조회해서 값을 찾음
    // 추후에 사용자 추가정보를 입력받을 때 소셜 타입과 ID를 조회해서 회원을 찾기 위해 사용함
    // 소셜 로그인 식별자(플랫폼 고유 ID)가 있을 경우 대비
    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);

    // 로컬 로그인 용도: 이메일 + 패스워드 일치 사용자 조회 (비추천: 일반적으로 서비스단에서 비밀번호 해시 비교)
    // Optional<User> findByEmailAndPassword(String email, String password);
}
