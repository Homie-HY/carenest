<template>
  <div class="app-container assistant-container">
    <el-container class="chat-layout">
      <!-- 会话列表侧栏 -->
      <el-aside width="260px" class="session-aside">
        <div class="aside-head">
          <el-button type="primary" icon="Plus" style="width: 100%" @click="handleNewSession">新建会话</el-button>
        </div>
        <el-scrollbar class="session-scroll">
          <ul class="session-list">
            <li
              v-for="s in sessions"
              :key="s.sessionId"
              class="session-item"
              :class="{ active: s.sessionId === currentSessionId }"
              @click="selectSession(s)"
            >
              <div class="session-title">
                <el-icon><ChatDotRound /></el-icon>
                <span class="title-text">{{ s.title || '新的对话' }}</span>
              </div>
              <div class="session-sub">
                <span>{{ formatTime(s.lastActiveTime || s.createTime) }}</span>
                <el-icon class="del-btn" @click.stop="handleDeleteSession(s)"><Delete /></el-icon>
              </div>
            </li>
            <li v-if="sessions.length === 0" class="session-empty">暂无会话，点击上方新建</li>
          </ul>
        </el-scrollbar>
      </el-aside>

      <!-- 消息主区 -->
      <el-main class="chat-main">
        <el-scrollbar ref="scrollRef" class="msg-scroll">
          <div class="msg-list">
            <div v-if="messages.length === 0" class="chat-placeholder">
              <el-icon :size="42"><Service /></el-icon>
              <p>你好，我是护理助手。可以问我老人档案、健康评估结论、护理计划/项目、床位与房间入住情况。</p>
              <p class="tip">我只做信息查询与解读，不提供医疗诊断；涉及用药与处置请联系医生或护士长。</p>
            </div>

            <div v-for="(m, idx) in messages" :key="idx" class="msg-row" :class="m.role">
              <div class="avatar">
                <el-icon v-if="m.role === 'user'"><UserFilled /></el-icon>
                <el-icon v-else><Service /></el-icon>
              </div>
              <div class="bubble">
                <!-- 工具调用状态 -->
                <div v-if="m.tools && m.tools.length" class="tool-trace">
                  <div v-for="(t, ti) in m.tools" :key="ti" class="tool-line">
                    <el-icon v-if="t.phase === 'start'" class="is-loading"><Loading /></el-icon>
                    <el-icon v-else-if="t.success === false" color="#f56c6c"><CircleCloseFilled /></el-icon>
                    <el-icon v-else color="#67c23a"><CircleCheckFilled /></el-icon>
                    <span>{{ t.toolDescription || t.toolName }}</span>
                    <span v-if="t.phase === 'complete' && t.costMs" class="cost">{{ t.costMs }}ms</span>
                  </div>
                </div>
                <div class="content" :class="{ error: m.error }">{{ m.content || (m.streaming ? '正在思考…' : '') }}</div>
              </div>
            </div>
          </div>
        </el-scrollbar>

        <!-- 输入区 -->
        <div class="input-area">
          <el-input
            v-model="input"
            type="textarea"
            :rows="3"
            resize="none"
            :disabled="sending"
            placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="input-actions">
            <el-button v-if="sending" type="danger" icon="VideoPause" @click="handleStop">停止生成</el-button>
            <el-button v-else type="primary" icon="Promotion" :disabled="!canSend" @click="handleSend">发送</el-button>
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup name="Assistant">
import { createSession, listSessions, delSession, getHistory, chatStream } from '@/api/nursing/assistant'

const { proxy } = getCurrentInstance()

const sessions = ref([])
const currentSessionId = ref('')
const messages = ref([])
const input = ref('')
const sending = ref(false)
const scrollRef = ref(null)

let streamHandle = null

const canSend = computed(() => !!currentSessionId.value && input.value.trim().length > 0 && !sending.value)

/** 加载会话列表 */
function loadSessions() {
  listSessions().then(res => {
    sessions.value = res.data || []
  })
}

/** 新建会话 */
function handleNewSession() {
  createSession().then(res => {
    const meta = res.data
    sessions.value.unshift(meta)
    currentSessionId.value = meta.sessionId
    messages.value = []
  })
}

/** 切换会话并回显历史 */
function selectSession(s) {
  if (sending.value) {
    proxy.$modal.msgWarning('请等待当前回答结束')
    return
  }
  currentSessionId.value = s.sessionId
  loadHistory(s.sessionId)
}

/** 加载某会话历史 */
function loadHistory(sessionId) {
  getHistory(sessionId, 50).then(res => {
    const list = res.data || []
    const arr = []
    list.forEach(log => {
      arr.push({ role: 'user', content: log.userInput })
      arr.push({
        role: 'assistant',
        content: log.success === 0 ? (log.errorMsg || '（本轮回答失败）') : log.modelOutput,
        error: log.success === 0,
        tools: parseTools(log.toolCalls)
      })
    })
    messages.value = arr
    scrollToBottom()
  })
}

/** 删除会话 */
function handleDeleteSession(s) {
  proxy.$modal.confirm('是否确认删除该会话？').then(() => {
    return delSession(s.sessionId)
  }).then(() => {
    sessions.value = sessions.value.filter(x => x.sessionId !== s.sessionId)
    if (currentSessionId.value === s.sessionId) {
      currentSessionId.value = ''
      messages.value = []
    }
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 发送消息（流式） */
function handleSend() {
  if (!canSend.value) return
  const text = input.value.trim()
  input.value = ''

  messages.value.push({ role: 'user', content: text })
  const assistantMsg = reactive({ role: 'assistant', content: '', tools: [], streaming: true, error: false })
  messages.value.push(assistantMsg)
  scrollToBottom()

  sending.value = true
  streamHandle = chatStream(currentSessionId.value, text, {
    onToken(token) {
      assistantMsg.content += token
      scrollToBottom()
    },
    onTool(evt) {
      if (evt && evt.phase === 'start') {
        assistantMsg.tools.push({ ...evt })
      } else if (evt && evt.phase === 'complete') {
        const last = assistantMsg.tools[assistantMsg.tools.length - 1]
        if (last && last.toolName === evt.toolName) {
          Object.assign(last, evt)
        } else {
          assistantMsg.tools.push({ ...evt })
        }
      }
      scrollToBottom()
    },
    onDone(result) {
      if (result && typeof result.answer === 'string' && result.answer) {
        assistantMsg.content = result.answer
      }
      assistantMsg.streaming = false
      sending.value = false
      streamHandle = null
      refreshSessionTitle(text)
      scrollToBottom()
    },
    onError(msg) {
      assistantMsg.content = assistantMsg.content || (msg || 'AI 助手暂时无法回答，请稍后重试')
      assistantMsg.error = true
      assistantMsg.streaming = false
      sending.value = false
      streamHandle = null
      scrollToBottom()
    }
  })
}

/** 停止生成 */
function handleStop() {
  if (streamHandle) {
    streamHandle.abort()
    streamHandle = null
  }
  const last = messages.value[messages.value.length - 1]
  if (last && last.role === 'assistant') {
    last.streaming = false
  }
  sending.value = false
}

/** 首条消息后用其更新侧栏标题（服务端已按首条消息生成标题） */
function refreshSessionTitle(text) {
  const s = sessions.value.find(x => x.sessionId === currentSessionId.value)
  if (s && (!s.title || s.title === '新的对话')) {
    s.title = text.length > 20 ? text.slice(0, 20) : text
  }
}

function parseTools(raw) {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr)
      ? arr.map(t => ({ phase: 'complete', toolName: t.toolName, toolDescription: t.toolDescription, success: t.success, costMs: t.costMs }))
      : []
  } catch (e) {
    return []
  }
}

function scrollToBottom() {
  nextTick(() => {
    const wrap = scrollRef.value && scrollRef.value.$el.querySelector('.el-scrollbar__wrap')
    if (wrap) wrap.scrollTop = wrap.scrollHeight
  })
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const p = n => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(() => {
  loadSessions()
})

onBeforeUnmount(() => {
  if (streamHandle) streamHandle.abort()
})
</script>

<style scoped>
.assistant-container {
  height: calc(100vh - 84px);
}
.chat-layout {
  height: 100%;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: hidden;
}
.session-aside {
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color-lighter);
}
.aside-head {
  padding: 12px;
}
.session-scroll {
  flex: 1;
}
.session-list {
  list-style: none;
  margin: 0;
  padding: 0 8px 12px;
}
.session-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
  transition: background 0.2s;
}
.session-item:hover {
  background: var(--el-fill-color);
}
.session-item.active {
  background: var(--el-color-primary-light-9);
}
.session-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.del-btn {
  visibility: hidden;
}
.session-item:hover .del-btn {
  visibility: visible;
  color: var(--el-color-danger);
}
.session-empty {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 24px 0;
}
.chat-main {
  display: flex;
  flex-direction: column;
  padding: 0;
}
.msg-scroll {
  flex: 1;
  padding: 16px 20px;
}
.chat-placeholder {
  text-align: center;
  color: var(--el-text-color-secondary);
  margin-top: 60px;
  line-height: 1.8;
}
.chat-placeholder .tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}
.msg-row.user {
  flex-direction: row-reverse;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex: 0 0 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
}
.msg-row.user .avatar {
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-regular);
}
.bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  line-height: 1.7;
}
.msg-row.user .bubble {
  background: var(--el-color-primary-light-9);
}
.content {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.content.error {
  color: var(--el-color-danger);
}
.tool-trace {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color);
}
.tool-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 2px;
}
.tool-line .cost {
  margin-left: auto;
  color: var(--el-text-color-placeholder);
}
.input-area {
  border-top: 1px solid var(--el-border-color-light);
  padding: 12px 16px;
}
.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
