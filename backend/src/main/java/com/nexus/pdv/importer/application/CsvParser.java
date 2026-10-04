package com.nexus.pdv.importer.application;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitor de CSV: detecta separador (";" ou ","), aceita aspas, UTF-8 (com ou sem BOM) e
 * Windows-1252 (planilhas antigas do Excel).
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static TabularFile parse(byte[] content, int maxRows) {
        String text = decode(content);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        int firstLineEnd = text.indexOf('\n');
        String firstLine = firstLineEnd < 0 ? text : text.substring(0, firstLineEnd);
        char delimiter = count(firstLine, ';') >= count(firstLine, ',') ? ';' : ',';

        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                current.add(field.toString().trim());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(field.toString().trim());
                field.setLength(0);
                addRecord(records, current, maxRows);
                current = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString().trim());
            addRecord(records, current, maxRows);
        }
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O arquivo está vazio.");
        }
        return new TabularFile(records.get(0), records.subList(1, records.size()));
    }

    private static void addRecord(List<List<String>> records, List<String> record, int maxRows) {
        if (record.stream().allMatch(String::isEmpty)) {
            return;
        }
        if (records.size() > maxRows) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O arquivo excede o limite de " + maxRows + " linhas.");
        }
        records.add(record);
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException ex) {
            return new String(content, Charset.forName("windows-1252"));
        }
    }

    private static int count(String value, char ch) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ch) {
                total++;
            }
        }
        return total;
    }
}
