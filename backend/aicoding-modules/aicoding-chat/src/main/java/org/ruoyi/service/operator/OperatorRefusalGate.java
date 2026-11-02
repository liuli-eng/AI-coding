package org.ruoyi.service.operator;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 拒绝兜底闸门（L4）。
 *
 * <p>问题：拒绝通常出现在流的第一段，如果边收边推，用户先看到一句「抱歉」，再重试也已经污染了输出。
 *
 * <p>做法：先把开头的 {@link #HOLD_LIMIT} 个字符扣住不推。
 * <ul>
 *   <li>缓冲超过阈值 → 判定不是拒绝，整段放行并转入直通模式；</li>
 *   <li>流结束时仍在阈值内 → 交给 {@link RefusalGuard} 判定；命中且还有重试额度则丢弃这段缓冲、
 *       触发重试，委托方（{@link ChatServiceFacade}）会用同一条消息重新请求模型；</li>
 *   <li>未命中或额度用尽 → 放行缓冲并正常结束。</li>
 * </ul>
 *
 * <p>关键约束：被丢弃的那一轮**不能**通知 delegate 的 onCompleteResponse，否则会把拒绝内容落库；
 * 也只有最后一轮会真正触发落库与 SSE 完成事件。
 */
@Slf4j
public class OperatorRefusalGate implements StreamingChatResponseHandler {

    /** 扣留窗口：足够覆盖一句拒绝，又不会明显影响首字延迟体感。 */
    private static final int HOLD_LIMIT = 600;

    private final StreamingChatResponseHandler delegate;
    private final RefusalGuard guard;
    private final int attempt;
    private final int maxAttempts;
    private final Runnable retryAction;
    private final String scene;
    private final String model;
    /** 本轮用户请求：用于把「内容安全型拒绝」和「能力型软拒」分开。 */
    private final String requestText;

    private final StringBuilder hold = new StringBuilder();
    private boolean passing;

    public OperatorRefusalGate(StreamingChatResponseHandler delegate, RefusalGuard guard,
                               int attempt, int maxAttempts, Runnable retryAction,
                               String scene, String model, String requestText) {
        this.delegate = delegate;
        this.guard = guard;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.retryAction = retryAction;
        this.scene = scene;
        this.model = model;
        this.requestText = requestText;
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        if (partialResponse == null) {
            return;
        }
        if (passing) {
            delegate.onPartialResponse(partialResponse);
            return;
        }
        hold.append(partialResponse);
        if (hold.length() >= HOLD_LIMIT) {
            passing = true;
            delegate.onPartialResponse(hold.toString());
            hold.setLength(0);
        }
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        delegate.onPartialThinking(partialThinking);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        if (!passing) {
            String text = hold.length() > 0
                ? hold.toString()
                : (completeResponse == null ? "" : completeResponse.aiMessage().text());
            boolean retry = guard != null
                && guard.recordAndShouldRetry(scene, model, attempt, text, requestText)
                && attempt < maxAttempts;
            if (retry) {
                hold.setLength(0);
                try {
                    retryAction.run();
                } catch (RuntimeException failure) {
                    // 重试本身失败时不能吞掉用户这一轮：把已扣留内容放行，按正常结束处理。
                    log.warn("operator_refusal scene={} model={} attempt={} retryFailed={}",
                        scene, model, attempt, failure.getClass().getSimpleName());
                    passing = true;
                    delegate.onCompleteResponse(completeResponse);
                }
                return;
            }
            passing = true;
            if (hold.length() > 0) {
                delegate.onPartialResponse(hold.toString());
                hold.setLength(0);
            }
        }
        delegate.onCompleteResponse(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        delegate.onError(error);
    }
}
