package com.carenest.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carenest.ai.domain.AiChatLog;

/**
 * AI 对话审计日志 Mapper。
 * <p>
 * 包路径落在 {@code com.carenest.**.mapper} 下，由 carenest-framework 的
 * {@code @MapperScan("com.carenest.**.mapper")} 统一扫描，无需额外配置。
 *
 * @author Homie
 */
public interface AiChatLogMapper extends BaseMapper<AiChatLog> {
}
