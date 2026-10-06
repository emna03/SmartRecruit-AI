package com.smartrecruit.cv.service;

import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.PayloadTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvFileValidatorTest {

    private static final DataSize MAX = DataSize.ofMegabytes(5);
    private static final byte[] PDF = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Test
    void acceptsPdfContentWhateverTheDeclaredType() {
        CvFileValidator.ValidatedPdf pdf = CvFileValidator.validate(
                new MockMultipartFile("file", "cv.docx", "application/msword", PDF), MAX);

        assertThat(pdf.content()).isEqualTo(PDF);
        assertThat(pdf.originalFileName()).isEqualTo("cv.docx.pdf");
    }

    @Test
    void acceptsSignatureAfterLeadingBytes() {
        byte[] content = ("\n\n  " + new String(PDF, StandardCharsets.US_ASCII)).getBytes(StandardCharsets.US_ASCII);

        assertThat(CvFileValidator.hasPdfSignature(content)).isTrue();
        assertThat(CvFileValidator.hasPdfSignature("%PDF".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(CvFileValidator.hasPdfSignature(new byte[2000])).isFalse();
    }

    @Test
    void rejectsNonPdfContentEvenWithPdfNameAndType() {
        MockMultipartFile fake = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                "<html>pas un pdf</html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> CvFileValidator.validate(fake, MAX))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(CvFileValidator.NOT_PDF_MESSAGE);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> CvFileValidator.validate(null, MAX))
                .isInstanceOf(BadRequestException.class).hasMessage(CvFileValidator.EMPTY_MESSAGE);
        assertThatThrownBy(() -> CvFileValidator.validate(
                new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[0]), MAX))
                .isInstanceOf(BadRequestException.class).hasMessage(CvFileValidator.EMPTY_MESSAGE);
        assertThatThrownBy(() -> CvFileValidator.validate(
                new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[1025]), DataSize.ofKilobytes(1)))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mon CV 2026.pdf|Mon CV 2026.pdf",
            "C:\\Users\\x\\CV Élodie.PDF|CV Élodie.PDF",
            "../../etc/passwd|passwd.pdf",
            "..pdf|pdf.pdf",
            "cv<script>alert(1)</script>.pdf|script_.pdf",
            "cv\"; rm -rf.pdf|cv__ rm -rf.pdf",
            "''|cv.pdf",
    })
    void sanitizesDisplayedFileName(String raw, String expected) {
        assertThat(CvFileValidator.sanitizeFileName(raw)).isEqualTo(expected);
    }

    @Test
    void truncatesVeryLongFileNames() {
        assertThat(CvFileValidator.sanitizeFileName("a".repeat(300) + ".pdf")).hasSize(120).endsWith(".pdf");
    }

    @Test
    void partialDatesFromTheParserBecomeFirstDayOfPeriod() {
        assertThat(CvService.toDate("2023")).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(CvService.toDate("2023-03")).isEqualTo(LocalDate.of(2023, 3, 1));
        assertThat(CvService.toDate(null)).isNull();
    }
}
