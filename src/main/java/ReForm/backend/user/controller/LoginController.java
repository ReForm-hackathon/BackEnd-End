package ReForm.backend.user.controller;

import ReForm.backend.user.DTO.AuthTokensDTO;
import ReForm.backend.user.DTO.LocalLoginRequestDTO;
import ReForm.backend.user.DTO.LocalSignupRequestDTO;
import ReForm.backend.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.util.Optional;

@Controller
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    // 로그인 화면 (로컬 로그인 + 소셜 버튼 + 회원가입 이동)
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // 회원가입 화면
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    // 로컬 회원가입
    @PostMapping("/register")
    @ResponseBody
    public ResponseEntity<?> register(@RequestBody LocalSignupRequestDTO request) {
        try {
            userService.signupLocal(request);
            return ResponseEntity.created(URI.create("/login")).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // 로컬 로그인
    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<?> login(@RequestBody LocalLoginRequestDTO request) {
        Optional<AuthTokensDTO> tokens = userService.loginLocal(request);
        return tokens.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("아이디 또는 비밀번호가 올바르지 않습니다."));
    }
}
