package ReForm.backend.user.service.sms;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * CoolSMS SDK를 이용한 SMS 발송 구현체
 * @Primary 어노테이션으로 기본 SmsSender 구현체로 등록
 */
@Component
@Primary
@Slf4j
public class CoolSmsSender implements SmsSender {

	// application.yml에서 주입받는 CoolSMS API 자격 정보 (없으면 빈 문자열)
	@Value("${coolsms.apikey:}")
	private String apiKey;

	@Value("${coolsms.apisecret:}")
	private String apiSecret;

	@Value("${coolsms.fromnumber:}")
	private String fromNumber; // 발신번호 (CoolSMS에 등록된 번호여야 함)

	private DefaultMessageService messageService; // CoolSMS 메시지 서비스 객체

	/**
	 * 스프링 빈 초기화 후 CoolSMS SDK 초기화
	 * NurigoApp.INSTANCE.initialize()를 통해 API 연결 설정
	 */
	@PostConstruct
	public void init() {
		if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
			// CoolSMS API 서버 URL과 자격 정보로 SDK 초기화
			this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
			log.info("CoolSMS SDK 초기화 완료");
		} else {
			log.warn("CoolSMS 자격 정보가 설정되지 않았습니다. SMS 발송이 비활성화됩니다.");
		}
	}

	/**
	 * SMS 발송 실행
	 * @param to 수신자 전화번호
	 * @param text 메시지 본문
	 */
	@Override
	public void send(String to, String text) {
		// SDK가 초기화되지 않았거나 발신번호가 없으면 실패 처리
		if (messageService == null || fromNumber == null || fromNumber.isBlank()) {
			throw new IllegalStateException("CoolSMS 설정이 누락되어 SMS를 보낼 수 없습니다. (apikey/apisecret/fromnumber 확인)");
		}

		try {
			// CoolSMS Message 객체 생성 및 설정
			Message message = new Message();
			message.setFrom(fromNumber);    // 발신번호
			message.setTo(to);              // 수신번호
			message.setText(text);          // 메시지 본문

			// 단일 메시지 발송 요청
			messageService.sendOne(new SingleMessageSendingRequest(message));
			log.info("SMS 발송 성공: {}", to);
		} catch (Exception e) {
			log.error("SMS 발송 중 오류 발생: {}", e.getMessage(), e);
			throw new IllegalStateException("SMS 발송 실패: " + e.getMessage(), e);
		}
	}
}
