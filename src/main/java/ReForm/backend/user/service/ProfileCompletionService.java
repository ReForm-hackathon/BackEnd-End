package ReForm.backend.user.service;

import ReForm.backend.user.SocialType;
import ReForm.backend.user.User;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProfileCompletionService {

    public Result check(User user) {
        List<String> missing = new ArrayList<>();
        SocialType type = user.getSocialType();

        boolean isLocal = type == SocialType.LOCAL;

        // Common required fields
        require(user.getUserId(), "userId", missing);
        require(user.getEmail(), "email", missing);
        require(user.getUserName(), "userName", missing);
        require(user.getRole() != null ? user.getRole().name() : null, "role", missing);
        require(type != null ? type.name() : null, "socialType", missing);
        require(user.getNickname(), "nickname", missing);
        require(user.getAddress(), "address", missing);
        require(user.getProfileImageUrl(), "profileImageUrl", missing);

        if (isLocal) {
            // For local: everything except socialId must exist
            require(user.getPassword(), "password", missing);
            require(user.getPhoneNumber(), "phoneNumber", missing);
            // socialId is allowed to be null/blank for local
        } else {
            // For social: everything except phone and password must exist
            require(user.getSocialId(), "socialId", missing);
            // password, phoneNumber allowed empty for social
        }

        return new Result(missing.isEmpty(), missing);
    }

    private void require(String value, String field, List<String> missing) {
        if (value == null || value.isBlank()) missing.add(field);
    }

    @Getter
    public static class Result {
        private final boolean complete;
        private final List<String> missingFields;

        public Result(boolean complete, List<String> missingFields) {
            this.complete = complete;
            this.missingFields = missingFields;
        }
    }
}



