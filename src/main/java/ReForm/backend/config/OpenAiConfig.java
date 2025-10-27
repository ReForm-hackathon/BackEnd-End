package ReForm.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OpenAiConfig {

    @Value("${spring.ai.openai.api.key}")
    private String openAiKey;


    @Bean
    public RestTemplate template(){
        RestTemplate restTemplate = new RestTemplate();

        //인터셉터는 모든 HTTP 요청 직전에 실행되는 일종의 전처리 함수 (Chain 패턴)
        //요청마다 Authorization 헤더를 자동으로 추가하도록 지정
        // 헤더 네임으로 Authorization , value 값으로 Bearer + 인증키로 헤더에 추가
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + openAiKey);
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}