package com.carenest.nursing.ai.assistant;

import com.carenest.ai.audit.ChatAuditRecorder;
import com.carenest.ai.config.DeepSeekProperties;
import com.carenest.ai.domain.AiChatLog;
import com.carenest.ai.memory.ChatMemoryFactory;
import com.carenest.ai.memory.ChatSessionMeta;
import com.carenest.ai.memory.ChatSessionRegistry;
import com.carenest.ai.tool.ToolAssembler;
import com.carenest.ai.tool.ToolCallListener;
import com.carenest.ai.tool.ToolCallRecord;
import com.carenest.ai.tool.ToolCallSink;
import com.carenest.common.core.domain.entity.SysRole;
import com.carenest.common.exception.ServiceException;
import com.carenest.common.utils.SecurityUtils;
import com.carenest.nursing.ai.context.AccessScope;
import com.carenest.nursing.ai.tool.BedTools;
import com.carenest.nursing.ai.tool.ElderTools;
import com.carenest.nursing.ai.tool.HealthAssessmentTools;
import com.carenest.nursing.ai.tool.NursingPlanTools;
import com.carenest.nursing.mapper.NursingProjectPlanMapper;
import com.carenest.nursing.service.IBedService;
import com.carenest.nursing.service.IElderService;
import com.carenest.nursing.service.IFloorService;
import com.carenest.nursing.service.IHealthAssessmentService;
import com.carenest.nursing.service.INursingElderService;
import com.carenest.nursing.service.INursingPlanService;
import com.carenest.nursing.service.INursingProjectService;
import com.carenest.nursing.service.IRoomService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 护理助手会话服务：管理会话生命周期、按会话缓存 AiServices 实例、驱动对话与审计。
 * <p>
 * 几个关键设计：
 * <ul>
 *   <li><b>身份只在请求线程解析</b>：{@link #currentScope()} 用 {@code SecurityUtils} 取当前登录人，
 *       构造 {@link AccessScope} 注入工具；工具执行线程（尤其流式回调）绝不碰 SecurityContext。</li>
 *   <li><b>AiServices 按会话缓存</b>：memoryId={userId}:{sessionId} 为 key，避免每条消息都反射扫描 {@code @Tool}。
 *       缓存 key 天然带 userId，不存在跨用户串用。</li>
 *   <li><b>每会话一把锁</b>：同一会话的请求串行化，保证 {@link ToolCallSink} 的轨迹收集不交错。</li>
 *   <li><b>密钥未配置时优雅降级</b>：不抛底层异常，返回明确提示，且不影响现有 MiMo 健康评估链路。</li>
 * </ul>
 *
 * @author Homie
 */
@Slf4j
@Service
public class AssistantService {

    /** AiServices 实例缓存上限，超出后按最近访问时间淘汰，防止长时间内存膨胀 */
    private static final int MAX_CACHED_SESSIONS = 500;

    /** 会话标题兜底长度 */
    private static final int TITLE_MAX_LEN = 20;

    private final Map<String, SessionContext> cache = new ConcurrentHashMap<>();

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ChatMemoryFactory chatMemoryFactory;
    private final ChatSessionRegistry sessionRegistry;
    private final ChatAuditRecorder auditRecorder;
    private final DeepSeekProperties properties;

    private final IElderService elderService;
    private final INursingElderService nursingElderService;
    private final IFloorService floorService;
    private final IRoomService roomService;
    private final IBedService bedService;
    private final IHealthAssessmentService healthAssessmentService;
    private final INursingPlanService nursingPlanService;
    private final INursingProjectService nursingProjectService;
    private final NursingProjectPlanMapper nursingProjectPlanMapper;

    public AssistantService(ChatLanguageModel chatLanguageModel,
                            StreamingChatLanguageModel streamingChatLanguageModel,
                            ChatMemoryFactory chatMemoryFactory,
                            ChatSessionRegistry sessionRegistry,
                            ChatAuditRecorder auditRecorder,
                            DeepSeekProperties properties,
                            IElderService elderService,
                            INursingElderService nursingElderService,
                            IFloorService floorService,
                            IRoomService roomService,
                            IBedService bedService,
                            IHealthAssessmentService healthAssessmentService,
                            INursingPlanService nursingPlanService,
                            INursingProjectService nursingProjectService,
                            NursingProjectPlanMapper nursingProjectPlanMapper) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.chatMemoryFactory = chatMemoryFactory;
        this.sessionRegistry = sessionRegistry;
        this.auditRecorder = auditRecorder;
        this.properties = properties;
        this.elderService = elderService;
        this.nursingElderService = nursingElderService;
        this.floorService = floorService;
        this.roomService = roomService;
        this.bedService = bedService;
        this.healthAssessmentService = healthAssessmentService;
        this.nursingPlanService = nursingPlanService;
        this.nursingProjectService = nursingProjectService;
        this.nursingProjectPlanMapper = nursingProjectPlanMapper;
    }

    /**
     * 非流式对话。
     */
    public ChatResult chat(String sessionId, String message) {
        String sid = requireSessionId(sessionId);
        String text = requireMessage(message);
        ensureAvailable();

        AccessScope scope = currentScope();
        String memoryId = ChatMemoryFactory.memoryId(scope.getUserId(), sid);
        sessionRegistry.touch(scope.getUserId(), sid, deriveTitle(text));

        SessionContext ctx = getOrCreate(memoryId, scope);
        AiChatLog audit = auditRecorder.newRound(scope.getUserId(), scope.getDeptId(),
                scope.roleKeysText(), sid, text);
        long startedAt = System.currentTimeMillis();

        ctx.lock.acquireUninterruptibly();
        ctx.sink.attach(ToolCallListener.NOOP);
        try {
            Result<String> result = ctx.assistant.chat(memoryId, text);
            List<ToolCallRecord> trace = ctx.sink.drain();
            auditRecorder.recordSuccess(audit, result.content(), trace, result.tokenUsage(), startedAt);
            return new ChatResult(sid, result.content(), trace);
        } catch (Exception e) {
            List<ToolCallRecord> trace = ctx.sink.drain();
            auditRecorder.recordFailure(audit, trace, e, startedAt);
            log.error("护理助手对话失败，sessionId={}, userId={}", sid, scope.getUserId(), e);
            throw new ServiceException("AI 助手暂时无法回答，请稍后重试");
        } finally {
            ctx.sink.detach();
            ctx.lock.release();
        }
    }

    /**
     * 流式对话：通过 {@link SseEmitter} 逐 token 推送，并在工具开始/结束时推送进度事件。
     * <p>
     * 身份与审计对象<b>必须在请求线程上取好</b>（此刻 SecurityContext 才有效），随后 TokenStream 的
     * 回调运行在 okhttp 线程，只做推送与落库，绝不再碰 SecurityContext。
     * <p>
     * 本方法立即返回，emitter 的生命周期由 {@link StreamHandler} 在终态回调中收敛。
     */
    public void chatStream(String sessionId, String message, SseEmitter emitter) {
        String sid = requireSessionId(sessionId);
        String text = requireMessage(message);
        ensureAvailable();

        AccessScope scope = currentScope();
        String memoryId = ChatMemoryFactory.memoryId(scope.getUserId(), sid);
        sessionRegistry.touch(scope.getUserId(), sid, deriveTitle(text));

        SessionContext ctx = getOrCreate(memoryId, scope);
        AiChatLog audit = auditRecorder.newRound(scope.getUserId(), scope.getDeptId(),
                scope.roleKeysText(), sid, text);
        long startedAt = System.currentTimeMillis();

        // 会话级串行化：同一会话同时只允许一轮对话在跑，保证 sink 轨迹不交错。
        // 用 Semaphore 而非 synchronized/ReentrantLock，因为释放发生在 okhttp 回调线程，
        // 而 ReentrantLock 只允许持有线程解锁。
        ctx.lock.acquireUninterruptibly();
        StreamHandler handler = new StreamHandler(emitter, ctx, audit, sid, memoryId, text, startedAt);
        try {
            handler.begin();
        } catch (RuntimeException e) {
            // begin() 内部已兜底 finish()；此处仅防御性释放，避免锁泄漏。
            handler.abortQuietly(e);
        }
    }

    /**
     * 新建会话，返回带 sessionId 的元信息。
     */
    public ChatSessionMeta createSession() {
        ensureAvailable();
        Long userId = SecurityUtils.getUserId();
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        return sessionRegistry.create(userId, sessionId, "新的对话");
    }

    /**
     * 列出当前用户的全部会话（按最近活跃倒序）。
     */
    public List<ChatSessionMeta> listSessions() {
        Long userId = SecurityUtils.getUserId();
        return sessionRegistry.list(userId);
    }

    /**
     * 删除会话：清索引、清记忆、清缓存的 AiServices 实例。
     */
    public void deleteSession(String sessionId) {
        String sid = requireSessionId(sessionId);
        Long userId = SecurityUtils.getUserId();
        sessionRegistry.remove(userId, sid);
        chatMemoryFactory.clear(userId, sid);
        cache.remove(ChatMemoryFactory.memoryId(userId, sid));
    }

    /**
     * 查询某会话的历史对话，用于前端刷新后回显。
     */
    public List<AiChatLog> history(String sessionId, int limit) {
        String sid = requireSessionId(sessionId);
        Long userId = SecurityUtils.getUserId();
        return auditRecorder.listBySession(userId, sid, limit <= 0 ? 50 : limit);
    }

    // ==================== 内部实现 ====================

    private void ensureAvailable() {
        if (!properties.isAvailable()) {
            throw new ServiceException("AI 护理助手尚未配置模型密钥（LLM_DEEPSEEK_API_KEY），请联系管理员");
        }
    }

    /**
     * 在请求线程上解析当前登录人的数据权限范围。
     */
    private AccessScope currentScope() {
        Long userId = SecurityUtils.getUserId();
        Long deptId = null;
        try {
            deptId = SecurityUtils.getDeptId();
        } catch (Exception e) {
            log.debug("当前用户无部门信息，deptId 记为空，userId={}", userId);
        }
        Set<String> roleKeys = resolveRoleKeys();
        boolean admin = SecurityUtils.isAdmin(userId) || roleKeys.contains("admin");
        return new AccessScope(userId, deptId, admin, roleKeys);
    }

    private Set<String> resolveRoleKeys() {
        try {
            List<SysRole> roles = SecurityUtils.getLoginUser().getUser().getRoles();
            if (roles == null || roles.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> keys = new HashSet<>();
            for (SysRole role : roles) {
                if (role != null && role.getRoleKey() != null) {
                    keys.add(role.getRoleKey());
                }
            }
            return keys;
        } catch (Exception e) {
            log.warn("解析用户角色失败，按最小权限处理", e);
            return Collections.emptySet();
        }
    }

    private SessionContext getOrCreate(String memoryId, AccessScope scope) {
        SessionContext existing = cache.get(memoryId);
        if (existing != null) {
            existing.lastAccess = System.currentTimeMillis();
            return existing;
        }
        SessionContext built = build(memoryId, scope);
        SessionContext prev = cache.putIfAbsent(memoryId, built);
        if (prev != null) {
            return prev;
        }
        evictIfNeeded();
        return built;
    }

    private SessionContext build(String memoryId, AccessScope scope) {
        ToolCallSink sink = new ToolCallSink();
        List<Object> tools = Arrays.asList(
                new ElderTools(elderService, nursingElderService, floorService, roomService, bedService, scope),
                new HealthAssessmentTools(healthAssessmentService, elderService, nursingElderService, scope),
                new NursingPlanTools(nursingPlanService, nursingProjectService, nursingProjectPlanMapper),
                new BedTools(bedService, roomService, floorService)
        );
        Map<ToolSpecification, ToolExecutor> executors = ToolAssembler.assemble(tools, sink);
        NursingAssistant assistant = AiServices.builder(NursingAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemoryProvider(chatMemoryFactory)
                .tools(executors)
                .build();
        log.debug("构建护理助手实例，memoryId={}, 工具数={}", memoryId, executors.size());
        return new SessionContext(assistant, sink, scope);
    }

    /**
     * 超出缓存上限时淘汰最久未访问的实例。淘汰只丢内存中的 AiServices 对象，
     * 会话记忆在 Redis、会话索引也在 Redis，用户下次访问会重新构建，无数据损失。
     */
    private void evictIfNeeded() {
        if (cache.size() <= MAX_CACHED_SESSIONS) {
            return;
        }
        List<Map.Entry<String, SessionContext>> entries = new ArrayList<>(cache.entrySet());
        entries.sort((a, b) -> Long.compare(a.getValue().lastAccess, b.getValue().lastAccess));
        int removeCount = entries.size() - MAX_CACHED_SESSIONS;
        for (int i = 0; i < removeCount; i++) {
            cache.remove(entries.get(i).getKey());
        }
    }

    private String deriveTitle(String message) {
        String text = message.trim();
        return text.length() > TITLE_MAX_LEN ? text.substring(0, TITLE_MAX_LEN) : text;
    }

    private String requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new ServiceException("会话 id 不能为空");
        }
        return sessionId.trim();
    }

    private String requireMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new ServiceException("对话内容不能为空");
        }
        return message.trim();
    }

    /**
     * 会话级缓存单元：绑定一个 AiServices 实例、其工具轨迹汇集点、数据权限范围与串行化锁。
     * <p>
     * lock 用 {@link Semaphore}(1) 而非对象监视器：流式对话的释放发生在 okhttp 回调线程，
     * 与获取锁的请求线程不同，Semaphore 允许跨线程释放。
     */
    static final class SessionContext {
        final NursingAssistant assistant;
        final ToolCallSink sink;
        final AccessScope scope;
        final Semaphore lock = new Semaphore(1);
        volatile long lastAccess = System.currentTimeMillis();

        SessionContext(NursingAssistant assistant, ToolCallSink sink, AccessScope scope) {
            this.assistant = assistant;
            this.sink = sink;
            this.scope = scope;
        }
    }

    /**
     * 单轮流式对话的处理器：把 TokenStream 回调、工具事件、审计落库与 SseEmitter 生命周期收敛到一处。
     * <p>
     * 所有回调都可能运行在 okhttp 线程，因此：
     * <ul>
     *   <li>不再访问 SecurityContext（身份已在请求线程取好并写进 audit）；</li>
     *   <li>用 {@code finished} 原子标志保证 finish() 只执行一次（终态回调、超时、连接断开可能重复触发）；</li>
     *   <li>推送失败（客户端已断开）只记日志，不抛出，避免污染工具执行链路。</li>
     * </ul>
     */
    private final class StreamHandler implements ToolCallListener {

        private final SseEmitter emitter;
        private final SessionContext ctx;
        private final AiChatLog audit;
        private final String sid;
        private final String memoryId;
        private final String text;
        private final long startedAt;
        private final StringBuilder answerBuf = new StringBuilder();
        private final AtomicBoolean finished = new AtomicBoolean(false);

        StreamHandler(SseEmitter emitter, SessionContext ctx, AiChatLog audit,
                      String sid, String memoryId, String text, long startedAt) {
            this.emitter = emitter;
            this.ctx = ctx;
            this.audit = audit;
            this.sid = sid;
            this.memoryId = memoryId;
            this.text = text;
            this.startedAt = startedAt;
        }

        /**
         * 注册 emitter 兜底回调、绑定 sink 监听、启动 TokenStream。
         */
        void begin() {
            // 超时/客户端断开/传输异常时，确保锁与 sink 一定被释放，且审计留痕。
            emitter.onTimeout(() -> abortQuietly(new ServiceException("AI 应答超时")));
            emitter.onError(e -> abortQuietly(e));
            emitter.onCompletion(this::releaseOnly);

            ctx.sink.attach(this);
            try {
                TokenStream stream = ctx.assistant.chatStream(memoryId, text);
                stream.onNext(this::onToken)
                        .onComplete(this::onStreamComplete)
                        .onError(this::abortQuietly)
                        .start();
            } catch (Exception e) {
                abortQuietly(e);
            }
        }

        private void onToken(String token) {
            if (token == null || token.isEmpty()) {
                return;
            }
            answerBuf.append(token);
            send("message", token, MediaType.TEXT_PLAIN);
        }

        private void onStreamComplete(Response<AiMessage> response) {
            String full = answerBuf.toString();
            if (response != null && response.content() != null && response.content().text() != null
                    && !response.content().text().isEmpty()) {
                full = response.content().text();
            }
            List<ToolCallRecord> trace = ctx.sink.drain();
            auditRecorder.recordSuccess(audit, full, trace,
                    response == null ? null : response.tokenUsage(), startedAt);
            ChatResult result = new ChatResult(sid, full, trace);
            send("done", result, MediaType.APPLICATION_JSON);
            finish();
        }

        @Override
        public void onToolStart(ToolCallRecord record) {
            send("tool", toolEvent("start", record), MediaType.APPLICATION_JSON);
        }

        @Override
        public void onToolComplete(ToolCallRecord record) {
            send("tool", toolEvent("complete", record), MediaType.APPLICATION_JSON);
        }

        private Map<String, Object> toolEvent(String phase, ToolCallRecord record) {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("phase", phase);
            payload.put("toolName", record.getToolName());
            payload.put("toolDescription", record.getToolDescription());
            payload.put("success", record.isSuccess());
            payload.put("costMs", record.getCostMs());
            return payload;
        }

        /**
         * 异常终态：落失败审计、推送 error 事件、收敛资源。可从任意线程调用。
         */
        void abortQuietly(Throwable error) {
            if (finished.get()) {
                return;
            }
            List<ToolCallRecord> trace = ctx.sink.drain();
            auditRecorder.recordFailure(audit, trace, error, startedAt);
            log.error("护理助手流式对话失败，sessionId={}", sid, error);
            send("error", "AI 助手暂时无法回答，请稍后重试", MediaType.TEXT_PLAIN);
            finish();
        }

        /**
         * 收敛：detach sink、释放会话锁、complete emitter。仅执行一次。
         */
        private void finish() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            ctx.sink.detach();
            ctx.lock.release();
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SseEmitter complete 忽略异常，sessionId={}", sid, e);
            }
        }

        /**
         * 连接正常关闭回调：此时 finish() 多半已执行；仅在极端情况下补释放，避免锁泄漏。
         * 不重复 detach/complete。
         */
        private void releaseOnly() {
            if (finished.compareAndSet(false, true)) {
                ctx.sink.detach();
                ctx.lock.release();
            }
        }

        private void send(String event, Object data, MediaType mediaType) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, mediaType));
            } catch (IOException | IllegalStateException e) {
                // 客户端已断开或 emitter 已完成：记日志即可，不中断模型侧流程。
                log.debug("SSE 推送失败，event={}, sessionId={}", event, sid, e);
            }
        }
    }
}
