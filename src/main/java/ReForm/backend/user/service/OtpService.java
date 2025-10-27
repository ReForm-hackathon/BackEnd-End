package ReForm.backend.user.service;

import ReForm.backend.user.service.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP(One-Time Password) 인증 서비스
 * 휴대폰 SMS를 통한 인증번호 발급 및 검증을 담당
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

	// 인증번호 유효 시간 (초 단위)
	private static final long TTL_SECONDS = 180; // 3분

	private final SmsSender smsSender; // SMS 발송 인터페이스 (CoolSmsSender가 주입됨)

	// 인메모리 인증번호 저장소: 전화번호 -> (인증번호, 만료시각)
	// 실서비스에서는 Redis 등 외부 캐시 사용 권장
	private final Map<String, Entry> phoneToCode = new ConcurrentHashMap<>();

	private final Random random = new Random();

	/**
	 * 인증번호 발급 및 SMS 전송
	 * @param phoneNumber 수신자 전화번호
	 * @return 생성된 6자리 인증번호 (테스트/디버그 용도, 실서비스에선 반환 제거 고려)
	 */
	public String issueCode(String phoneNumber) {
		// 6자리 난수 생성 (000000 ~ 999999)
		String code = String.format("%06d", random.nextInt(1_000_000));

		// 만료 시각 계산 (현재 시각 + TTL)
		long expiresAt = Instant.now().getEpochSecond() + TTL_SECONDS;

		// 인증번호 저장 (기존 번호가 있으면 덮어씀)
		phoneToCode.put(phoneNumber, new Entry(code, expiresAt));

		// SMS 발송 (CoolSmsSender가 실제 전송 처리)
		String messageText = "[Re:Form] 본인 확인을 위해 [" + code + "] 인증번호를 입력하세요.";
		smsSender.send(phoneNumber, messageText);

		log.info("인증번호 발급: 인증번호={}, 만료시각={}", code, expiresAt);

		return code; // 필요 시 제거 가능
	}

	/**
	 * 인증번호 검증
	 * @param phoneNumber 전화번호
	 * @param code 사용자가 입력한 인증번호
	 * @return 인증 성공 여부
	 */
	public boolean verify(String phoneNumber, String code) {
		Entry entry = phoneToCode.get(phoneNumber);

		// 1. 발급 이력이 없는 경우
		if (entry == null) {
			log.warn("인증 실패: 발급 이력 없음 - {}", phoneNumber);
			return false;
		}

		// 2. 만료 시간 체크
		long now = Instant.now().getEpochSecond();
		if (now > entry.expiresAt) {
			phoneToCode.remove(phoneNumber); // 만료된 인증번호 삭제
			log.warn("인증 실패: 시간 만료 - {}", phoneNumber);
			return false;
		}

		// 3. 인증번호 일치 여부 확인
		boolean isMatch = entry.code.equals(code);

		if (isMatch) {
			// 인증 성공 시 1회성으로 삭제 (재사용 방지)
			phoneToCode.remove(phoneNumber);
			log.info("인증 성공: {}", phoneNumber);
		} else {
			log.warn("인증 실패: 코드 불일치 - {}", phoneNumber);
		}

		return isMatch;
	}

	/**
	 * 인증번호 저장용 내부 레코드
	 * @param code 인증번호
	 * @param expiresAt 만료 시각 (Unix timestamp, 초 단위)
	 */
	private record Entry(String code, long expiresAt) {}
}
