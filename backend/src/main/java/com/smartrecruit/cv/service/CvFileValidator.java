package com.smartrecruit.cv.service;

import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.PayloadTooLargeException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Contrôle d'un CV envoyé par le navigateur. Le type MIME et l'extension déclarés ne sont jamais pris en compte :
 * seul le contenu fait foi (signature {@code %PDF-} dans les premiers octets). L'analyse complète du PDF
 * (structure, chiffrement, texte) est faite ensuite par le service IA.
 */
public final class CvFileValidator {

    public static final String PDF_CONTENT_TYPE = "application/pdf";
    static final String EMPTY_MESSAGE = "Le fichier est vide.";
    static final String NOT_PDF_MESSAGE = "Le fichier n'est pas un PDF valide. Seuls les CV au format PDF sont acceptés.";

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int SIGNATURE_WINDOW = 1024;
    private static final int MAX_FILE_NAME_LENGTH = 120;

    private CvFileValidator() {
    }

    public record ValidatedPdf(byte[] content, String originalFileName) {
    }

    public static ValidatedPdf validate(MultipartFile file, DataSize maxSize) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(EMPTY_MESSAGE);
        }
        if (file.getSize() > maxSize.toBytes()) {
            throw new PayloadTooLargeException(tooLargeMessage(maxSize));
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Le fichier n'a pas pu être lu.");
        }
        if (content.length == 0) {
            throw new BadRequestException(EMPTY_MESSAGE);
        }
        if (content.length > maxSize.toBytes()) {
            throw new PayloadTooLargeException(tooLargeMessage(maxSize));
        }
        if (!hasPdfSignature(content)) {
            throw new BadRequestException(NOT_PDF_MESSAGE);
        }
        return new ValidatedPdf(content, sanitizeFileName(file.getOriginalFilename()));
    }

    public static String tooLargeMessage(DataSize maxSize) {
        return "Le fichier dépasse la taille maximale de %d Mo.".formatted(maxSize.toMegabytes());
    }

    static boolean hasPdfSignature(byte[] content) {
        int limit = Math.min(content.length, SIGNATURE_WINDOW) - PDF_SIGNATURE.length;
        for (int start = 0; start <= limit; start++) {
            int i = 0;
            while (i < PDF_SIGNATURE.length && content[start + i] == PDF_SIGNATURE[i]) {
                i++;
            }
            if (i == PDF_SIGNATURE.length) {
                return true;
            }
        }
        return false;
    }

    /** Nom affiché uniquement (jamais utilisé comme chemin) : sans répertoire ni caractères spéciaux, finissant par .pdf. */
    static String sanitizeFileName(String raw) {
        String name = raw == null ? "" : raw;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC)
                .replaceAll("[^\\p{L}\\p{N} ._()\\-]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("^[.\\s_]+", "")
                .strip();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            name = (name.isEmpty() ? "cv" : name) + ".pdf";
        }
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            name = name.substring(0, MAX_FILE_NAME_LENGTH - 4) + ".pdf";
        }
        return name;
    }
}
