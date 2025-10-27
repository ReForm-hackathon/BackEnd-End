package ReForm.backend.user.controller;

import ReForm.backend.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageController {

    private final UserService userService;

    public MessageController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/message")
    public ResponseEntity<Object> sendVerifyNum(@RequestParam(value = "phoneNumber") String phoneNumber) {
        try {
            if (phoneNumber.contains("-")) {
                return new ResponseEntity<>("하이폰 제거 후 번호 다시 입력", HttpStatus.OK);
            }
            userService.issuePhoneCode(phoneNumber);
            return new ResponseEntity<>("인증번호 발송 완료", HttpStatus.OK);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<Object> checkVerifyNum(@RequestParam(value = "phoneNumber") String phoneNumber,
                                                 @RequestParam(value = "verifyNumber") String verifyNumber) {
        try {
            boolean check = userService.verifyPhoneCode(phoneNumber, verifyNumber);
            return new ResponseEntity<>(check, HttpStatus.OK);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}