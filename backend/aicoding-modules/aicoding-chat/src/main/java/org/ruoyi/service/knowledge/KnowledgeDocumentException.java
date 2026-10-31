package org.ruoyi.service.knowledge;
/** Only fixed, non-sensitive reasons are returned to the browser. */
public final class KnowledgeDocumentException extends RuntimeException {
    public enum Reason {
        INVALID_FILE("请选择有效的文件，文件名不能包含控制字符"),
        EMPTY_FILE("文件为空，请重新选择"),
        FILE_TOO_LARGE("单个文档不能超过20MB"),
        TYPE_DISABLED("此文件格式未启用，请检查系统设置"),
        DUPLICATE("内容重复：此知识库已存在相同内容的文档"),
        SAME_NAME("此知识库已有同名文件，请更名或在系统设置中开启覆盖"),
        PARSING("文档正在解析，请处理完成后重试"),
        UNAVAILABLE("文档或知识库不存在，或无权操作"),
        SETTINGS_CONFLICT("设置已被其他窗口修改，请刷新后重试"),
        INVALID_SETTINGS("设置无效，请检查名称、格式、提示和相似度阈值"),
        STORAGE_FAILED("文件保存失败，请重试"),
        READ_FAILED("文件读取失败，请重新选择");
        private final String message;
        Reason(String message){this.message=message;}
    }
    private final Reason reason;
    public KnowledgeDocumentException(Reason reason){super(reason.message);this.reason=reason;}
    public Reason reason(){return reason;}
}
