package ReForm.backend.community.repository;

import ReForm.backend.community.CommunityLike;
import ReForm.backend.community.CommunityLike.CommunityLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityLikeRepository extends JpaRepository<CommunityLike, CommunityLikeId> {

    Optional<CommunityLike> findById(CommunityLikeId id);
}
