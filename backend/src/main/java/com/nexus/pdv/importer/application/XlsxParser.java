package com.nexus.pdv.importer.application;

import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Leitor mínimo de XLSX (primeira planilha) sem dependências externas.
 * Segurança: DTD/entidades externas desabilitadas (XXE) e limite de tamanho descompactado
 * por entrada (zip bomb).
 */
public final class XlsxParser {

    private static final long MAX_ENTRY_BYTES = 30L * 1024 * 1024;

    private XlsxParser() {
    }

    public static TabularFile parse(byte[] content, int maxRows) {
        Map<String, byte[]> entries = unzip(content);
        List<String> sharedStrings = entries.containsKey("xl/sharedStrings.xml")
                ? readSharedStrings(entries.get("xl/sharedStrings.xml"))
                : List.of();
        String sheetName = entries.keySet().stream()
                .filter(name -> name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
                .sorted()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Planilha XLSX inválida."));
        List<List<String>> rows = readSheet(entries.get(sheetName), sharedStrings, maxRows);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A planilha está vazia.");
        }
        return new TabularFile(rows.get(0), rows.subList(1, rows.size()));
    }

    private static Map<String, byte[]> unzip(byte[] content) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("xl/sharedStrings.xml") || name.startsWith("xl/worksheets/sheet")) {
                    entries.put(name, readLimited(zip));
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Arquivo XLSX inválido ou corrompido.");
        }
        return entries;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Planilha grande demais.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static XMLStreamReader reader(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    }

    private static List<String> readSharedStrings(byte[] xml) {
        List<String> strings = new ArrayList<>();
        try {
            XMLStreamReader reader = reader(xml);
            StringBuilder current = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "si".equals(reader.getLocalName())) {
                    current = new StringBuilder();
                } else if (event == XMLStreamConstants.START_ELEMENT && "t".equals(reader.getLocalName()) && current != null) {
                    current.append(reader.getElementText());
                } else if (event == XMLStreamConstants.END_ELEMENT && "si".equals(reader.getLocalName()) && current != null) {
                    strings.add(current.toString());
                    current = null;
                }
            }
        } catch (XMLStreamException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Planilha XLSX inválida.");
        }
        return strings;
    }

    private static List<List<String>> readSheet(byte[] xml, List<String> sharedStrings, int maxRows) {
        List<List<String>> rows = new ArrayList<>();
        try {
            XMLStreamReader reader = reader(xml);
            TreeMap<Integer, String> row = null;
            String cellRef = null;
            String cellType = null;
            String value = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "row" -> row = new TreeMap<>();
                        case "c" -> {
                            cellRef = reader.getAttributeValue(null, "r");
                            cellType = reader.getAttributeValue(null, "t");
                            value = null;
                        }
                        case "v" -> value = reader.getElementText();
                        case "t" -> {
                            if ("inlineStr".equals(cellType)) {
                                value = reader.getElementText();
                            }
                        }
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("c".equals(reader.getLocalName()) && row != null && cellRef != null) {
                        row.put(columnIndex(cellRef), cellText(value, cellType, sharedStrings));
                    } else if ("row".equals(reader.getLocalName()) && row != null) {
                        List<String> values = toList(row);
                        if (!values.stream().allMatch(String::isEmpty)) {
                            if (rows.size() > maxRows) {
                                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                                        "O arquivo excede o limite de " + maxRows + " linhas.");
                            }
                            rows.add(values);
                        }
                        row = null;
                    }
                }
            }
        } catch (XMLStreamException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Planilha XLSX inválida.");
        }
        return rows;
    }

    private static String cellText(String value, String type, List<String> sharedStrings) {
        if (value == null) {
            return "";
        }
        if ("s".equals(type)) {
            int index = Integer.parseInt(value.trim());
            return index < sharedStrings.size() ? sharedStrings.get(index).trim() : "";
        }
        if ("b".equals(type)) {
            return "1".equals(value) ? "true" : "false";
        }
        if (type == null || "n".equals(type)) {
            try {
                return new BigDecimal(value.trim()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ex) {
                return value.trim();
            }
        }
        return value.trim();
    }

    private static List<String> toList(TreeMap<Integer, String> row) {
        if (row.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i <= row.lastKey(); i++) {
            values.add(row.getOrDefault(i, ""));
        }
        return values;
    }

    /** "C12" → 2 (base zero). */
    static int columnIndex(String cellRef) {
        int index = 0;
        for (char ch : cellRef.toCharArray()) {
            if (!Character.isLetter(ch)) {
                break;
            }
            index = index * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return index - 1;
    }
}
