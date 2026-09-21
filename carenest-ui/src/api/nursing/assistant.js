import request from '@/utils/request'
import { getToken } from '@/utils/auth'

// 新建会话
export function createSession() {
  return request({
    url: '/nursing/assistant/session',
    method: 'post'
  })
}

// 查询我的会话列表
export function listSessions() {
  return request({
    url: '/nursing/assistant/sessions',
    method: 'get'
  })
}

// 删除会话
export function delSession(sessionId) {
  return request({
    url: '/nursing/assistant/session/' + sessionId,
    method: 'delete'
  })
}

// 查询某会话历史对话（刷新后回显）
export function getHistory(sessionId, limit = 50) {
  return request({
    url: '/nursing/assistant/history/' + sessionId,
    method: 'get',
    params: { limit }
  })
}

// 非流式对话（备用，SSE 不可用时降级）
export function chat(sessionId, message) {
  return request({
    url: '/nursing/assistant/chat',
    method: 'post',
    data: { sessionId, message }
  })
}

/**
 * 流式对话（SSE）。
 *
 * 项目走 JWT，而原生 EventSource 不支持自定义 Authorization 头，
 * 因此这里用 fetch + ReadableStream 手工解析 SSE 帧。
 *
 * @param {string} sessionId 会话 id
 * @param {string} message   用户输入
 * @param {object} handlers  回调集合
 *   - onToken(text)        每个增量文本片段
 *   - onTool(evt)          工具调用事件 { phase:'start'|'complete', toolName, toolDescription, success, costMs }
 *   - onDone(result)       结束事件 { sessionId, answer, toolCalls }
 *   - onError(message)     错误事件（服务端主动下发的 error）
 * @returns {{ abort: Function }} 调用 abort() 可中断本次生成
 */
export function chatStream(sessionId, message, handlers = {}) {
  const controller = new AbortController()
  const base = import.meta.env.VITE_APP_BASE_API
  const query = new URLSearchParams({ sessionId, message }).toString()
  const url = `${base}/nursing/assistant/chat/stream?${query}`

  // 异步执行，立即返回 abort 句柄
  ;(async () => {
    try {
      const resp = await fetch(url, {
        method: 'GET',
        headers: {
          'Authorization': 'Bearer ' + getToken(),
          'Accept': 'text/event-stream'
        },
        signal: controller.signal
      })
      if (!resp.ok || !resp.body) {
        handlers.onError && handlers.onError('连接失败（HTTP ' + resp.status + '）')
        return
      }
      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // SSE 帧以空行分隔，逐帧取出解析
        let sep
        while ((sep = buffer.indexOf('\n\n')) >= 0) {
          const rawFrame = buffer.slice(0, sep)
          buffer = buffer.slice(sep + 2)
          dispatchFrame(rawFrame, handlers)
        }
      }
      // 冲刷残余（服务端未必以空行收尾）
      if (buffer.trim()) {
        dispatchFrame(buffer, handlers)
      }
    } catch (e) {
      // 主动 abort 不视为错误
      if (e && e.name === 'AbortError') return
      handlers.onError && handlers.onError((e && e.message) || '网络异常')
    }
  })()

  return { abort: () => controller.abort() }
}

// 解析单个 SSE 帧：event: 决定类型，多行 data: 用 \n 拼接
function dispatchFrame(rawFrame, handlers) {
  const lines = rawFrame.split('\n')
  let event = 'message'
  const dataLines = []
  for (const line of lines) {
    if (!line || line.startsWith(':')) continue // 空行或注释（心跳）
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  if (dataLines.length === 0) return
  const data = dataLines.join('\n')

  switch (event) {
    case 'message':
      handlers.onToken && handlers.onToken(data)
      break
    case 'tool':
      handlers.onTool && handlers.onTool(safeJson(data))
      break
    case 'done':
      handlers.onDone && handlers.onDone(safeJson(data))
      break
    case 'error':
      handlers.onError && handlers.onError(data)
      break
    default:
      break
  }
}

function safeJson(text) {
  try {
    return JSON.parse(text)
  } catch (e) {
    return text
  }
}
