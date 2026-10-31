package org.ruoyi.service.learning;

import static org.ruoyi.service.learning.CourseMaterialValidationException.Reason.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.zip.ZipInputStream;

/** Same PDFBox/POI parsers as the existing loaders, with closed resources and bounded output. */
@Component
public class CourseMaterialParser {
    public static final int MAX_TEXT=60000;
    public String parse(byte[] bytes,String suffix) {
        try {
            String text;
            if("pdf".equals(suffix)) {
                try(var document=Loader.loadPDF(bytes)) {
                    if(document.getNumberOfPages()>1000)throw new CourseMaterialValidationException(TOO_MANY_PDF_PAGES);
                    var output=new LimitedWriter();new PDFTextStripper().writeText(document,output);text=output.toString();
                }
            } else if("docx".equals(suffix)) {
                checkArchive(bytes);
                try(var input=new ByteArrayInputStream(bytes);var document=new XWPFDocument(input);var extractor=new XWPFWordExtractor(document)) {
                    text=extractor.getText();
                }
            } else {
                text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if(text.indexOf('\0')>=0)throw new CourseMaterialValidationException(BINARY_TEXT);
            }
            text=text.replace("\uFEFF", "").strip();
            if(text.isBlank())throw new CourseMaterialValidationException(NO_EXTRACTABLE_TEXT);
            if(text.length()>MAX_TEXT)throw new CourseMaterialValidationException(TEXT_TOO_LONG);
            return text;
        } catch(CourseMaterialValidationException e) {throw e;}
          catch(Exception e) {throw new CourseMaterialValidationException(MALFORMED_DOCUMENT);}
    }
    private void checkArchive(byte[] bytes) throws IOException {
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            long expanded=0;int entries=0;byte[] buffer=new byte[8192];
            while(zip.getNextEntry()!=null) {
                if(++entries>1000)throw new CourseMaterialValidationException(TOO_MANY_DOCX_ENTRIES);
                int count;while((count=zip.read(buffer))!=-1) {
                    expanded+=count;
                    if(expanded>30L*1024*1024)throw new CourseMaterialValidationException(DOCX_EXPANSION_TOO_LARGE);
                }
            }
        }
    }
    private static final class LimitedWriter extends Writer {
        private final StringBuilder text=new StringBuilder();
        @Override public void write(char[] value,int offset,int length) {
            if(text.length()+length>MAX_TEXT)throw new CourseMaterialValidationException(TEXT_TOO_LONG);
            text.append(value,offset,length);
        }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString(){return text.toString();}
    }
}
