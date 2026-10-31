package org.ruoyi.controller.learning;

import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.learning.CourseMaterialValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Leaves ordinary service, storage, runtime and bean-validation failures to global redaction. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={CourseMaterialController.class, LearningCourseController.class,
    LearningMaterialController.class, MyLearningController.class})
public class CourseMaterialExceptionHandler {
    @ExceptionHandler(CourseMaterialValidationException.class)
    public R<Void> materialValidation(CourseMaterialValidationException error) {
        return R.fail(error.getMessage());
    }
}
