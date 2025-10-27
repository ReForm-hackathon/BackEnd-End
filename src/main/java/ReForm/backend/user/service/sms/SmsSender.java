package ReForm.backend.user.service.sms;

/**
 * SMS 발송 인터페이스
 * 실제 SMS 게이트웨이(CoolSMS, Twilio 등)와의 통신을 추상화
 */
public interface SmsSender {

	/**
	 * SMS 메시지 발송
	 * @param to 수신자 전화번호 (예: 01012345678)
	 * @param text 메시지 본문
	 */
	void send(String to, String text);
}
