package com.carenest.common.config;

import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * PDFBox 配置类
 * 用于解决字体文件损坏导致的 EOFException 问题
 */
@Configuration
public class PdfBoxConfig {

    @PostConstruct
    public void init() {
        // 禁用 PDFBox 的系统字体检索和缓存
        // 这样可以避免扫描系统字体目录时遇到损坏的字体文件
        System.setProperty("pdfbox.fontcache", "false");
        
        // 可选：设置自定义字体目录（如果需要）
        // System.setProperty("pdfbox.fontdir", "/path/to/custom/fonts");
    }
}
