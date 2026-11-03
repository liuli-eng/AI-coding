package org.ruoyi.service.operator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 拒绝兜底（L4）。
 *
 * <p>提示词能显著降低拒绝率，但效果会随模型版本漂移，所以必须有一层可观测的判定：
 * 一旦命中，要么触发强化重试，要么至少留下可统计的拒绝事件，供后续调整注入文本。
 *
 * <p>判定只在前 {@link #WINDOW} 个字符内做，避免把正文里正常出现的「无法」「cannot」误判成拒绝。
 * 命中标记的同时若出现交付物特征（代码块、编号步骤、SQL、HTML 等），视为正常交付，不判拒绝。
 */
@Slf4j
@Service
public class RefusalGuard {

    /** 判定窗口：拒绝通常出现在开头。 */
    private static final int WINDOW = 240;

    /** 多语言拒绝标记。覆盖「不可以/不会提供」这类不带「抱歉/无法」的硬拒绝开场。 */
    private static final List<String> REFUSAL_MARKERS = List.of(
        "抱歉", "对不起", "很遗憾", "我无法", "无法提供", "无法协助", "不能提供", "不能协助",
        "我不能", "不予提供", "不便提供", "作为ai", "作为人工智能", "作为语言模型",
        "我理解你", "我需要拒绝", "不可以", "不会提供", "我不能帮", "无法帮你", "不予",
        "i'm sorry", "i am sorry", "i apologize", "i can't", "i cannot", "i won't", "i will not",
        "cannot provide", "can't provide", "cannot help", "can't help", "unable to",
        "i must decline", "not able to help", "against my guidelines", "as an ai");

    /** 交付物特征：出现即认为是在正常产出而不是拒绝。 */
    private static final List<String> DELIVERABLE_MARKERS = List.of(
        "```", "public class", "function ", "def ", "select ", "#!/", "<html", "#include",
        "step 1", "第1步", "第一步", "```java", "```python", "```sql");

    private final OperatorPromptOverlay overlay;
    private final OperatorRefusalStatService stats;

    public RefusalGuard(OperatorPromptOverlay overlay, OperatorRefusalStatService stats) {
        this.overlay = overlay;
        this.stats = stats;
    }

    /** 供闸门上报首轮总数/重试成功数（重试轮的成功在闸门里才能观测到）。 */
    OperatorRefusalStatService stats() {
        return stats;
    }

    public boolean enabled() {
        return overlay.enabled() && overlay.profile().refusalRetryMax() > 0;
    }

    public int retryMax() {
        return Math.max(0, overlay.profile().refusalRetryMax());
    }

    /** 判定一段模型输出是否为拒绝。文本为空时返回 false（空输出是另一个失败分支）。 */
    public boolean looksLikeRefusal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String window = text.strip();
        if (window.length() > WINDOW) {
            window = window.substring(0, WINDOW);
        }
        String normalized = window.toLowerCase(Locale.ROOT);
        boolean marker = REFUSAL_MARKERS.stream().anyMatch(normalized::contains);
        if (!marker) {
            return false;
        }
        return DELIVERABLE_MARKERS.stream().noneMatch(normalized::contains);
    }

    /**
     * 请求侧的主题标记：命中说明这是「内容安全」类拒绝，而不是「能力不足」类软拒。
     *
     * <p>判定放在请求上而不是回答上，因为模型拒成人向内容时通常只说「我不能写这类内容」，
     * 回答里没有任何可识别词；但请求里一定带着「成人向/不淡化」这类信号。
     */
    private static final List<String> CONTENT_SAFETY_TOPICS = List.of(
        "成人向", "成人内容", "露骨", "色情", "性描写", "情色", "不淡化", "不跳切", "荤",
        "explicit sex", "sexually explicit", "pornographic", "nsfw", "erotic");

    /** 拒绝性质。 */
    public enum RefusalClass {
        /** 不是拒绝。 */
        NONE,
        /** 内容安全类：模型在该主题上有硬边界，重试通常会越试越硬。 */
        CONTENT_SAFETY,
        /** 恶意用途类（勒索/木马/远控/钓鱼等）：同为硬边界，重试只会让措辞更硬。 */
        HARD_TOPIC,
        /** 能力/合规判断类：通过换框重做有机会通过。 */
        CAPABILITY
    }

    /**
     * 恶意用途类主题：模型训练时拒绝权重最高的一档。
     *
     * <p>实测（deepseek-flash + 3.2KB 注入）这一类连续三次都拒，且第二次起措辞更硬、
     * 会明确点出「不管包装成什么用途」——说明注入被识别并反向利用。所以只给一次机会。
     */
    private static final List<String> HARD_TOPIC_MARKERS = List.of(
        "木马", "勒索", "锁机", "病毒", "蠕虫", "远控", "免杀", "键盘记录", "后门", "持久化",
        "钓鱼", "仿站", "洗钱", "跑分", "盗刷", "撞库", "脱库", "社工库", "破解", "破解版",
        "注册机", "外挂", "免杀版", "入侵", "黑进", "日站", "拿站", "提权",
        "ransomware", "malware", "keylogger", "reverse shell", "exploit", "免杀");

    /** 结合请求与回答判定拒绝性质。 */
    public RefusalClass classify(String text, String requestText) {
        if (!looksLikeRefusal(text)) {
            return RefusalClass.NONE;
        }
        String topic = requestText == null ? "" : requestText.toLowerCase(Locale.ROOT);
        if (CONTENT_SAFETY_TOPICS.stream().anyMatch(topic::contains)) {
            return RefusalClass.CONTENT_SAFETY;
        }
        if (HARD_TOPIC_MARKERS.stream().anyMatch(topic::contains)) {
            return RefusalClass.HARD_TOPIC;
        }
        return RefusalClass.CAPABILITY;
    }

    /**
     * 记录一次拒绝事件，并决定是否值得再试。
     *
     * <p>内容安全类与恶意用途类只给一次机会：实测连续重问会让模型越拒越短越硬，第三次基本只剩一句
     * 「我不能写这类内容」，纯烧 token 且把拒绝写进历史。只有 CAPABILITY 类才用满配置的重试额度。
     *
     * @return true 表示还允许重试
     */
    public boolean recordAndShouldRetry(String scene, String model, int attempt, String text,
                                        String requestText) {
        boolean refusal = looksLikeRefusal(text);
        if (!refusal) {
            if (attempt > 0 && stats != null) {
                stats.recordRetrySuccess(scene, model);
            }
            return false;
        }
        if (stats != null) {
            stats.recordRefusal(scene, model);
        }
        int max = retryMax();
        RefusalClass refusalClass = classify(text, requestText);
        boolean hardBoundary = refusalClass == RefusalClass.CONTENT_SAFETY
            || refusalClass == RefusalClass.HARD_TOPIC;
        int effectiveMax = hardBoundary ? Math.min(max, 1) : max;
        boolean retry = attempt < effectiveMax;
        log.warn("operator_refusal scene={} model={} attempt={} max={} retry={} class={} preview={}",
            scene, model, attempt, effectiveMax, retry, refusalClass, preview(text));
        return retry;
    }

    private String preview(String text) {
        String stripped = text == null ? "" : text.strip().replace('\n', ' ');
        return stripped.length() <= 80 ? stripped : stripped.substring(0, 80) + "…";
    }
}
