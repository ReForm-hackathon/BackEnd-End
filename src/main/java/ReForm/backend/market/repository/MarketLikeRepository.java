package ReForm.backend.market.repository;

import ReForm.backend.market.MarketLike;
import ReForm.backend.market.MarketLike.MarketLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketLikeRepository extends JpaRepository<MarketLike, MarketLikeId> {

    Optional<MarketLike> findById(MarketLikeId id);
}


