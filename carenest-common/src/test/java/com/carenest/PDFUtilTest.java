package com.carenest;

import com.carenest.common.utils.PDFUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import static com.carenest.common.utils.PDFUtil.pdfToString;

public class PDFUtilTest {
    public static void main(String[] args) throws FileNotFoundException {
        //读一个文件
        FileInputStream fis = new FileInputStream("src/test/resources/health-report-sample.pdf");

        String result = PDFUtil.pdfToString(fis);
        System.out.println(result);
    }
}
