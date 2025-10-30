package ReForm.backend.user.service;

import ReForm.backend.user.Role;
import ReForm.backend.user.User;
import ReForm.backend.user.DTO.LocalLoginRequestDTO;
import ReForm.backend.user.DTO.LocalSignupRequestDTO;
import ReForm.backend.user.DTO.UserAdditionalDTO;
import ReForm.backend.user.DTO.UserBasicDTO;
import ReForm.backend.user.DTO.AuthTokensDTO;
import ReForm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final OtpService otpService; // SMS 인증번호 발급/검증 서비스

    // 로컬 회원가입: 이메일/비밀번호/이름/전화번호 + OTP 검증
    @Transactional
    public User signupLocal(LocalSignupRequestDTO request) {
        // 전화번호 인증번호 검증
        boolean verified = otpService.verify(request.getPhoneNumber(), request.getVerificationCode());
        if (!verified) {
            throw new IllegalArgumentException("전화번호 인증이 완료되지 않았습니다.");
        }

        String userId = request.getUserId();

        User user = User.builder()
                .userId(userId)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .userName(request.getUserName())
                .phoneNumber(request.getPhoneNumber())
                .role(Role.USER)
                .socialType(ReForm.backend.user.SocialType.LOCAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .profileImageUrl("/images/default-profile.png")
                .build();

        return userRepository.save(user);
    }

    /**
     * 전화번호 인증 코드 발급
     * OtpService를 통해 6자리 인증번호를 생성하고 SMS로 전송
     * @param phoneNumber 인증 대상 전화번호
     * @return 생성된 인증번호 (테스트 용도, 실서비스에선 반환 제거 권장)
     */
    public String issuePhoneCode(String phoneNumber) {
        return otpService.issueCode(phoneNumber);
    }

    /**
     * 전화번호 인증 확인
     * 사용자가 입력한 인증번호가 발급된 번호와 일치하고 만료되지 않았는지 검증
     * @param phoneNumber 인증 대상 전화번호
     * @param code 사용자가 입력한 인증번호
     * @return 인증 성공 여부
     */
    public boolean verifyPhoneCode(String phoneNumber, String code) {
        return otpService.verify(phoneNumber, code);
    }

    // 로컬 로그인: 사용자ID/비밀번호 확인 → AT/RT 발급 후 RT 저장
    @Transactional
    public Optional<AuthTokensDTO> loginLocal(LocalLoginRequestDTO request) {
        return userRepository.findById(request.getUserId())
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPassword()))
                .map(u -> {
                    String accessToken = jwtService.createAccessTokenByUserId(u.getUserId());
                    String refreshToken = jwtService.createRefreshToken();
                    jwtService.updateRefreshTokenByUserId(u.getUserId(), refreshToken);
                    // 로그인 성공 시 토큰 로그 출력 (운영 환경에서는 마스킹 권장)
                    log.info("[LOGIN] userId={}, accessToken=Bearer {}, refreshToken=Bearer {}", u.getUserId(), accessToken, refreshToken);
                    return new AuthTokensDTO(accessToken, refreshToken);
                });
    }

    // 소셜 최초 가입: id/email/name 저장, password는 공백
    @Transactional
    public User signupSocial(UserBasicDTO social) {
        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .userId(userId)
                .email(social.getEmail())
                .userName(social.getName())
                .password("")
                .socialType(social.getSocialType())
                .socialId(social.getSocialId())
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .profileImageUrl("/images/default-profile.png")
                .build();
        return userRepository.save(user);
    }

    // 소셜 로그인: 존재 시 토큰 발급, 최초 로그인이라면 상위 signupSocial로 가입 처리 후 토큰 발급
    @Transactional
    public AuthTokensDTO loginOrSignupSocial(UserBasicDTO social) {
        return userRepository.findByEmailAndSocialType(social.getEmail(), social.getSocialType())
                .map(u -> {
                    String at = jwtService.createAccessTokenByUserId(u.getUserId());
                    String rt = jwtService.createRefreshToken();
                    jwtService.updateRefreshTokenByUserId(u.getUserId(), rt);
                    return new AuthTokensDTO(at, rt);
                })
                .orElseGet(() -> {
                    User newUser = signupSocial(social);
                    String at = jwtService.createAccessTokenByUserId(newUser.getUserId());
                    String rt = jwtService.createRefreshToken();
                    jwtService.updateRefreshTokenByUserId(newUser.getUserId(), rt);
                    return new AuthTokensDTO(at, rt);
                });
    }

    // 소셜/로컬 공통: 로그인 후 추가정보 입력
    @Transactional
    public User updateAdditionalInfo(String userId, UserAdditionalDTO dto) {
        User user = userRepository.findById(userId).orElseThrow();
        user = User.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .password(user.getPassword())
                .socialType(user.getSocialType())
                .socialId(user.getSocialId())
                .role(user.getRole())
                .nickname(dto.getNickname())
                .address(dto.getAddress())
                .createdAt(user.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        return userRepository.save(user);
    }
}
