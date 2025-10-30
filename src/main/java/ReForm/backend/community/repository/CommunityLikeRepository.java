package ReForm.backend.community.repository;

import ReForm.backend.community.CommunityLike;
import ReForm.backend.community.CommunityLike.CommunityLikeId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityLikeRepository extends JpaRepository<CommunityLike, CommunityLikeId> {

    Optional<CommunityLike> findById(CommunityLikeId id);

    /**
     * 특정 게시글의 좋아요 개수
     */
    long countByCommunity_CommunityId(Integer communityId);

    /**
     * 좋아요 수 상위 커뮤니티 ID 목록을 반환 (내림차순)
     * 반환 원소: [communityId(Integer), likeCount(Long)]
     */
    @Query("select cl.community.communityId as communityId, count(cl) as likeCount " +
           "from CommunityLike cl group by cl.community.communityId " +
           "order by likeCount desc")
    java.util.List<Object[]> findTopLikedCommunities(Pageable pageable);
}
