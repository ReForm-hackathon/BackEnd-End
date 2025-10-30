package ReForm.backend.ai.repository;

import ReForm.backend.ai.entity.AIAnalysisHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIAnalysisHistoryRepository extends JpaRepository<AIAnalysisHistory, Long> {

    /**
     * 사용자별 최신 히스토리 조회 (최대 10개)
     * @param userId 사용자 ID
     * @return 최신 10개 히스토리 목록
     */
    @Query("SELECT h FROM AIAnalysisHistory h WHERE h.user.userId = :userId ORDER BY h.createdAt DESC")
    List<AIAnalysisHistory> findTop10ByUserIdOrderByCreatedAtDesc(@Param("userId") String userId, Pageable pageable);

    /**
     * 사용자별 전체 히스토리 개수 조회
     * @param userId 사용자 ID
     * @return 히스토리 개수
     */
    @Query("SELECT COUNT(h) FROM AIAnalysisHistory h WHERE h.user.userId = :userId")
    long countByUserId(@Param("userId") String userId);
}
