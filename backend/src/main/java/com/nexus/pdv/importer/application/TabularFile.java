package com.nexus.pdv.importer.application;

import java.util.List;

/** Conteúdo tabular lido de CSV/XLSX: cabeçalhos e linhas (valores já como texto). */
public record TabularFile(List<String> headers, List<List<String>> rows) {
}
