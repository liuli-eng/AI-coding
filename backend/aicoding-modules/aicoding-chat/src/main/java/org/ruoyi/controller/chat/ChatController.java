package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.service.chat.impl.ChatServiceFacade;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


/**
 * 聊天管理
 *
 * @author ageerle@163.com
 * @date 2023-03-01
 */
@Controller
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatServiceFacade chatService;
    private final org.ruoyi.service.chat.ChatQuestionService questionService;
    private final org.ruoyi.service.chat.ChatPromptService promptService;
    @GetMapping("/prompts") @ResponseBody
    public org.ruoyi.common.core.domain.R<java.util.List<org.ruoyi.domain.entity.chat.ChatPrompt>> prompts(@RequestParam(required=false) String mode) {
        org.ruoyi.common.satoken.utils.LoginHelper.getUserId();
        return org.ruoyi.common.core.domain.R.ok(promptService.options(mode));
    }

    @PostMapping("/question/{id}/feedback")
    @ResponseBody
    public org.ruoyi.common.core.domain.R<Void> feedback(@PathVariable Long id) {
        questionService.feedback(id, org.ruoyi.common.satoken.utils.LoginHelper.getUserId());
        return org.ruoyi.common.core.domain.R.ok();
    }

    /**
     * 聊天接口
     */
    @PostMapping("/send")
    @ResponseBody
    public SseEmitter sseChat(@RequestBody @Valid ChatRequest chatRequest) {
        return chatService.sseChat(chatRequest);
    }

}
