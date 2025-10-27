package ReForm.backend.ai.repository;

import ReForm.backend.ai.AIChatAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageAnalysisRepository extends JpaRepository<AIChatAnswer, Integer> {

    List<AIChatAnswer> findByUser_UserId(String userId);
}
