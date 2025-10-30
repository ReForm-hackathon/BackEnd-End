package ReForm.backend.market.repository;

import ReForm.backend.market.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketRepository extends JpaRepository<Market, Integer> {

    /**
     * 사용자별 마켓 게시글 조회
     * @param userId 사용자 ID
     * @return 해당 사용자의 마켓 게시글 목록
     */
    List<Market> findByUser_UserIdOrderByCreatedAtDesc(String userId);

    /**
     * 기부 상품만 조회
     * @return 기부 상품 목록
     */
    List<Market> findByIsDonationTrueOrderByCreatedAtDesc();

    /**
     * 판매 상품만 조회
     * @return 판매 상품 목록
     */
    List<Market> findByIsDonationFalseOrderByCreatedAtDesc();

    /**
     * 전체 목록 최신순 조회
     */
    List<Market> findAllByOrderByCreatedAtDesc();
}
