package org.ruoyi.service.chat;

import org.ruoyi.common.core.exception.ServiceException;
import java.util.Map;

/** Server-owned instructions, reapplied on every turn from the persisted session. */
public final class ChatModePolicy {
    private ChatModePolicy() {}
    private static final Map<String, String> PROMPTS = Map.of(
        "learning", "你是耐心的编程老师。先了解用户基础与学习目标，循序渐进解释概念，给出小示例，优先用问题引导思考，再提供提示和练习。不要一开始直接给出整套答案。结合本会话进度继续教学。",
        "programming", "你是编程助手。结合用户需求与现有代码，说明必要假设，提供可执行、易维护的实现，并说明使用方法与验证步骤。不确定的信息应明确说明。",
        "debugging", "你是代码排错助手。先核对报错、运行环境和复现步骤，区分已确认事实与推测，定位根因，提供最小修复和验证方法。缺少关键证据时先询问，不要编造执行结果。"
    );
    public static String normalize(String mode) {
        if (mode == null || mode.isBlank()) return "programming";
        if (!PROMPTS.containsKey(mode)) throw new ServiceException("不支持的对话模式");
        return mode;
    }
    public static String prompt(String mode) { return PROMPTS.get(normalize(mode)); }
}
