package org.ruoyi.service.learning;

/** Only these application-authored validation reasons may cross the HTTP boundary. */
public final class CourseMaterialValidationException extends RuntimeException {
    public enum Reason {
        EMPTY_FILE("文件为空，请选择包含文字的文件"),
        FILE_TOO_LARGE("单文件不能超过10MB"),
        INVALID_FILENAME("文件名无效或超过255个字符"),
        UNSUPPORTED_TYPE("仅支持 PDF、DOCX、TXT、MD 文件"),
        UNREADABLE_UPLOAD("读取上传文件失败，请重试"),
        MALFORMED_DOCUMENT("文件无法解析，请确认文件未损坏且格式正确；TXT/MD 请使用 UTF-8 编码"),
        BINARY_TEXT("文本文件包含二进制内容，请上传 UTF-8 文本"),
        NO_EXTRACTABLE_TEXT("文件没有可提取的文字，扫描件请先进行 OCR 文字识别"),
        TEXT_TOO_LONG("文件文字超过60000个字符，请拆分后上传"),
        TOO_MANY_PDF_PAGES("PDF 页数超过1000页，请拆分后上传"),
        TOO_MANY_DOCX_ENTRIES("DOCX 文件结构过大，请拆分后上传"),
        DOCX_EXPANSION_TOO_LARGE("DOCX 解压内容超过30MB，请拆分后上传"),
        INVALID_MATERIAL_ID("资料标识无效"),
        TOO_MANY_MATERIALS("每门课程最多10个文件"),
        DUPLICATE_MATERIAL("请勿重复绑定同一资料"),
        AGGREGATE_TEXT_TOO_LONG("课程资料合计不能超过60000个文字字符");

        private final String message;
        Reason(String message) { this.message = message; }
    }

    public CourseMaterialValidationException(Reason reason) {
        super(reason.message);
    }
}
