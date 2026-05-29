package com.reasonix.code.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarReader {

    private static final Logger log = LoggerFactory.getLogger(JarReader.class);

    public record JarEntryResult(byte[] data, String path) {}

    public static JarEntryResult readEntry(Path jarPath, String entryPath) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) {
                return null;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (var is = zip.getInputStream(entry)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
            }
            return new JarEntryResult(baos.toByteArray(), entryPath);
        } catch (IOException e) {
            log.debug("Failed to read entry {} from {}: {}", entryPath, jarPath, e.getMessage());
            return null;
        }
    }

    public static String readEntryAsString(Path jarPath, String entryPath) {
        JarEntryResult result = readEntry(jarPath, entryPath);
        if (result == null) return null;
        return new String(result.data());
    }

    public static String toClassEntry(String fullyQualifiedName) {
        return fullyQualifiedName.replace('.', '/') + ".class";
    }

    public static String toJavaEntry(String fullyQualifiedName) {
        return fullyQualifiedName.replace('.', '/') + ".java";
    }
}