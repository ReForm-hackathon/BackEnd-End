package ReForm.backend.config;

import jakarta.annotation.PostConstruct;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class SmsUtil {

    @Value("NCSXQAW1EOE6PNEA")
    private String apiKey;

    @Value("DR6BYEDTDCW2YPROEGWI7K3KQRU2IWD3")
    private String apiSecret;

    private DefaultMessageService messageService;

    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
    }

    // 단일 메시지 발송
    public void sendMessage(String to, String verificationCode) {
        Message message = new Message();
        message.setFrom("");
        message.setTo(to);
        message.setText("[에너세이버] 회원가입 인증을 위한 아래의 인증번호를 입력해주세요\n" + "["+verificationCode+"]");

        SingleMessageSentResponse response = this.messageService.sendOne(new SingleMessageSendingRequest(message));
    }
}
