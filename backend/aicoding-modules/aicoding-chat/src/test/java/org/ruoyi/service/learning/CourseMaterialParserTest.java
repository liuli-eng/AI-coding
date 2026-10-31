package org.ruoyi.service.learning;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class CourseMaterialParserTest {
    private final CourseMaterialParser parser=new CourseMaterialParser();
    @Test void parsesActualPdfAndDocxAndRejectsBlankScan() throws Exception {
        try(var doc=new PDDocument();var output=new ByteArrayOutputStream()) {
            var page=new PDPage();doc.addPage(page);
            try(var content=new PDPageContentStream(doc,page)){content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);content.newLineAtOffset(30,700);content.showText("Course PDF material");content.endText();}
            doc.save(output);assertEquals("Course PDF material",parser.parse(output.toByteArray(),"pdf"));
        }
        try(var doc=new PDDocument();var output=new ByteArrayOutputStream()){doc.addPage(new PDPage());doc.save(output);assertTrue(assertThrows(CourseMaterialValidationException.class,()->parser.parse(output.toByteArray(),"pdf")).getMessage().contains("OCR"));}
        try(var doc=new XWPFDocument();var output=new ByteArrayOutputStream()){doc.createParagraph().createRun().setText("DOCX课程全文");doc.write(output);assertEquals("DOCX课程全文",parser.parse(output.toByteArray(),"docx"));}
    }
    @Test void strictUtf8TextAndMarkdownPreserveFullTextWithoutTruncation(){
        assertEquals("# 标题\n全文",parser.parse("# 标题\n全文".getBytes(StandardCharsets.UTF_8),"md"));
        assertEquals(60000,parser.parse("x".repeat(60000).getBytes(StandardCharsets.UTF_8),"txt").length());
        assertThrows(CourseMaterialValidationException.class,()->parser.parse("x".repeat(60001).getBytes(StandardCharsets.UTF_8),"txt"));
        assertThrows(CourseMaterialValidationException.class,()->parser.parse(new byte[]{(byte)0xff},"txt"));
    }
}
