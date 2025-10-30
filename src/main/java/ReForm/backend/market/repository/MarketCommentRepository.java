package ReForm.backend.market.repository;

import ReForm.backend.market.MarketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketCommentRepository extends JpaRepository<MarketComment, Integer> {

    List<MarketComment> findByMarket_MarketIdOrderByCreatedAtDesc(Integer marketId);
}


