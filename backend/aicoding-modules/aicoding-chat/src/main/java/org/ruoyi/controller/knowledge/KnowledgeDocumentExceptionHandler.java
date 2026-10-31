package org.ruoyi.controller.knowledge;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.knowledge.KnowledgeDocumentException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages="org.ruoyi.controller.knowledge")
public class KnowledgeDocumentExceptionHandler {
    @ExceptionHandler(KnowledgeDocumentException.class)
    public R<Void> failure(KnowledgeDocumentException error){return R.fail(error.getMessage());}
}
