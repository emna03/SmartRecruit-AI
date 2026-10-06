package com.smartrecruit.cv.service;

import com.smartrecruit.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fichiers des CV sur disque ({@code UPLOAD_DIR/cvs}). Le nom stocké est aléatoire (jamais le nom envoyé
 * par le navigateur) et vérifié avant tout accès : aucune traversée de répertoire possible.
 */
@Slf4j
@Component
public class CvStorage {

    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f]{32}\\.pdf");

    private final Path directory;

    public CvStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.directory = Path.of(uploadDir).resolve("cvs").toAbsolutePath().normalize();
    }

    public String store(byte[] content) {
        String name = UUID.randomUUID().toString().replace("-", "") + ".pdf";
        try {
            Files.createDirectories(directory);
            Files.write(resolve(name), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.error("Impossible d'enregistrer un CV dans {} : {}", directory, e.getClass().getSimpleName());
            throw new UncheckedIOException("Enregistrement du CV impossible", e);
        }
        return name;
    }

    public byte[] read(String name) {
        try {
            return Files.readAllBytes(resolve(name));
        } catch (NoSuchFileException e) {
            log.warn("Fichier de CV absent du disque : {}", name);
            throw new ResourceNotFoundException("Le fichier du CV est introuvable : importez-le à nouveau.");
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du CV impossible", e);
        }
    }

    public boolean exists(String name) {
        return name != null && Files.isRegularFile(resolve(name));
    }

    /** Suppression silencieuse : un fichier orphelin ne doit pas faire échouer l'opération en cours. */
    public void delete(String name) {
        if (name == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(name));
        } catch (IOException | RuntimeException e) {
            log.warn("Suppression du fichier de CV {} impossible : {}", name, e.getClass().getSimpleName());
        }
    }

    Path directory() {
        return directory;
    }

    private Path resolve(String name) {
        if (!STORED_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nom de fichier de CV invalide");
        }
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory)) {
            throw new IllegalArgumentException("Nom de fichier de CV invalide");
        }
        return path;
    }
}
