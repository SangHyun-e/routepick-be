package io.routepickapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.routepickapi.entity.post.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostQueryRepositoryTest {

    @Autowired
    private PostQueryRepository postQueryRepository;

    @Autowired
    private PostRepository postRepository;

    @Test
    void searchOrdersNoticePinnedBeforeNoticeAndCreatedAt() {
        Post pinnedNotice = new Post("공지-고정", "pinned notice");
        pinnedNotice.toggleNotice();
        pinnedNotice.toggleNoticePinned();
        postRepository.save(pinnedNotice);

        Post notice = new Post("공지", "notice");
        notice.toggleNotice();
        postRepository.save(notice);

        Post normal = new Post("일반", "normal");
        postRepository.save(normal);

        Page<Post> page = postQueryRepository.searchByRegionAndKeyword(
            null,
            null,
            PageRequest.of(0, 10)
        );

        assertThat(page.getContent())
            .extracting(Post::getId)
            .containsExactly(pinnedNotice.getId(), notice.getId(), normal.getId());
    }
}
