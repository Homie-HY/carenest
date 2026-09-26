package com.carenest.nursing.ai.assistant;

import com.carenest.ai.domain.AiChatLog;
import com.carenest.ai.memory.ChatSessionMeta;
import com.carenest.common.annotation.Log;
import com.carenest.common.core.controller.BaseController;
import com.carenest.common.core.domain.AjaxResult;
import com.carenest.common.core.domain.R;
import com.carenest.common.enums.BusinessType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 对话式护理助手 Controller。
 * <p>
 * 阶段 2 只提供<b>非流式</b>对话与会话管理；流式 SSE 接口在阶段 3 补充。
 * 所有接口都要求 {@code nursing:assistant:chat} 权限，数据权限在服务层按登录人收敛。
 *
 * @author Homie
 */
@Api("护理助手")
@Slf4j
@RestController
@RequestMapping("/nursing/assistant")
public class AssistantController extends BaseController {

    /** SSE 超时（毫秒）：医疗问答含工具回环，给足 180 秒 */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @ApiOperation("护理助手对话（非流式）")
    @PreAuthorize("@ss.hasPermi('nursing:assistant:chat')")
    @Log(title = "护理助手对话", businessType = BusinessType.OTHER)
    @PostMapping("/chat")
    public R<ChatResult> chat(@RequestBody @ApiParam("对话请求") ChatRequest request) {
        ChatResult result = assistantService.chat(request.getSessionId(), request.getMessage());
        return R.ok(result);
    }

    @ApiOperation("护理助手对话（流式 SSE）")
    @PreAuthorize("@ss.hasPermi('nursing:assistant:chat')")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam("sessionId") @ApiParam("会话id") String sessionId,
                                 @RequestParam("message") @ApiParam("对话内容") String message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        try {
            assistantService.chatStream(sessionId, message, emitter);
        } catch (Exception e) {
            // 同步阶段失败（如密钥未配置、会话id为空）：以 SSE error 事件告知前端，保持流式契约。
            log.warn("护理助手流式对话启动失败，sessionId={}", sessionId, e);
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(e.getMessage() == null ? "AI 助手暂时无法回答" : e.getMessage()));
            } catch (IOException ignored) {
                // 客户端已断开，忽略
            }
            emitter.complete();
        }
        return emitter;
    }

    @ApiOperation("新建会话")
    @PreAuthorize("@ss.hasPermi('nursing:assistant:chat')")
    @PostMapping("/session")
    public R<ChatSessionMeta> createSession() {
        return R.ok(assistantService.createSession());
    }

    @ApiOperation("查询我的会话列表")
    @PreAuthorize("@ss.hasPermi('nursing:assistant:chat')")
    @GetMapping("/sessions")
    public R<List<ChatSessionMeta>> listSessions() {
        return R.ok(assistantService.listSessions());
    }

    @ApiOperation("删除会话")
    @PreAuthorize("@ss.hasPermi('nursing:assistant:chat')")
    @Log(title = "护理助手会话", businessType = BusinessType.DELETE)
    @DeleteMapping("/session/{sessionId}")
    public AjaxResult deleteSession(@PathVariable("sessionId") @ApiParam("会话id") String sessionId) {
        assistantService.deleteSession(sessionId);
        return AjaxResult.success();
    }

    @ApiOperation("查询会话历史对话")
    @PreAuthorize("@ss.hasPermi('nursing:assistant:chat')")
    @GetMapping("/history/{sessionId}")
    public R<List<AiChatLog>> history(@PathVariable("sessionId") @ApiParam("会话id") String sessionId,
                                      @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return R.ok(assistantService.history(sessionId, limit));
    }
}
