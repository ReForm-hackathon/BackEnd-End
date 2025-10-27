package ReForm.backend.user.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokensDTO {

	// 로그인 성공 시 클라이언트에 전달할 토큰 쌍
	private String accessToken;

	private String refreshToken;
}


