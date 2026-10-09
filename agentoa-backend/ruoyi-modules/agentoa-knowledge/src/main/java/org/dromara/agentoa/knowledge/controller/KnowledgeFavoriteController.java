package org.dromara.agentoa.knowledge.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.knowledge.service.IDocumentSocialService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的收藏（P1，KB-07）。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/knowledge/favorites")
public class KnowledgeFavoriteController {

    private final IDocumentSocialService socialService;

    @GetMapping
    public R<List<Long>> list() {
        return R.ok(socialService.myFavorites());
    }
}
