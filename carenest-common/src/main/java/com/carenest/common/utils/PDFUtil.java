package com.carenest.common.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PDFUtil {

    // ✅✅✅ 【这里换成最强禁用字体配置】✅✅✅
    static {
        System.setProperty("org.apache.pdfbox.font.disable.system", "true");
        System.setProperty("pdfbox.fontcache.disable", "true");
        System.setProperty("org.apache.pdfbox.rendering.UsePureJavaFonts", "true");
    }

    /**
     * 将PDF文件转换为字符串
     * @param inputStream PDF文件输入流
     * @return 提取的文本内容，失败返回null
     */
    public static String pdfToString(InputStream inputStream) {
        PDDocument document = null;

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            byte[] pdfBytes = buffer.toByteArray();

            if (pdfBytes.length == 0) {
                System.err.println("PDF文件为空");
                return null;
            }

            String fileHeader = new String(pdfBytes, 0, Math.min(5, pdfBytes.length));
            if (!fileHeader.startsWith("%PDF")) {
                System.err.println("文件不是有效的PDF格式，文件头: " + fileHeader);
                return null;
            }

            System.out.println("PDF文件大小: " + pdfBytes.length + " bytes");

            // ✅ 【唯一修改：加一个参数，不解析字体】
            document = PDDocument.load(pdfBytes, null);  // 这里加了 null！！！

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                System.err.println("PDF文档没有页面");
                return null;
            }

            System.out.println("PDF文档页数: " + pageCount);

            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setSortByPosition(false);
            pdfStripper.setStartPage(1);
            pdfStripper.setEndPage(pageCount);
            pdfStripper.setLineSeparator("\n");
            pdfStripper.setParagraphStart("\n\n");

            String text = pdfStripper.getText(document);

            System.out.println("提取的文本长度: " + (text != null ? text.length() : 0));

            return text;
        } catch (IOException e) {
            System.err.println("PDF解析失败: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("PDF处理异常: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    System.err.println("关闭PDF文档失败: " + e.getMessage());
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    System.err.println("关闭输入流失败: " + e.getMessage());
                }
            }
        }
    }
}