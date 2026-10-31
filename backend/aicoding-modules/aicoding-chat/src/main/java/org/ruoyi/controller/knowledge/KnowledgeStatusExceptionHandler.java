package org.ruoyi.controller.knowledge;

import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.knowledge.KnowledgeBaseDisabledException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Only the fixed disabled state is public; other errors retain global redaction. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages="org.ruoyi.controller")
public class KnowledgeStatusExceptionHandler {
    @ExceptionHandler(KnowledgeBaseDisabledException.class)
    public R<Void> disabled(KnowledgeBaseDisabledException error) { return R.fail("知识库已停用"); }
}
