package org.ruoyi.controller.knowledge;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.vo.knowledge.KnowledgeBaseSummaryVo;
import org.ruoyi.service.knowledge.KnowledgeBaseAccessService;
import org.springframework.web.bind.annotation.*;

/** Authenticated ordinary-user knowledge selection, separate from administrative configuration. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/knowledge/bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseAccessService knowledgeAccess;

    @GetMapping
    public TableDataInfo<KnowledgeBaseSummaryVo> list(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return knowledgeAccess.list(keyword, pageQuery);
    }

    @GetMapping("/{id}")
    public R<KnowledgeBaseSummaryVo> getInfo(@PathVariable String id) {
        return R.ok(knowledgeAccess.getSummary(id));
    }
}
