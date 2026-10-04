package com.nexus.pdv.importer.domain;

import com.nexus.pdv.permission.domain.Permission;
import java.util.List;

/** Tipos de importação, seus campos e a permissão de cadastro exigida além de IMPORT_EXECUTE. */
public enum ImportEntityType {

    PRODUCTS(Permission.PRODUCT_CREATE, List.of(
            new ImportField("name", "Nome", true, "nome", "produto", "descricao", "descrição"),
            new ImportField("sku", "SKU", false, "codigo", "código", "referencia", "referência"),
            new ImportField("barcode", "Código de barras", false, "ean", "gtin", "codigo de barras", "código de barras"),
            new ImportField("category", "Categoria", false, "categoria", "grupo"),
            new ImportField("salePrice", "Preço de venda", true, "preco", "preço", "preco venda", "preço de venda", "valor"),
            new ImportField("costPrice", "Preço de custo", false, "custo", "preco custo", "preço de custo"),
            new ImportField("minimumStock", "Estoque mínimo", false, "minimo", "mínimo", "estoque minimo", "estoque mínimo"),
            new ImportField("unit", "Unidade", false, "unidade", "un"),
            new ImportField("initialStock", "Estoque inicial", false, "estoque", "quantidade", "saldo"))),

    CATEGORIES(Permission.CATEGORY_CREATE, List.of(
            new ImportField("name", "Nome", true, "nome", "categoria"),
            new ImportField("description", "Descrição", false, "descricao", "descrição"))),

    STOCK(Permission.STOCK_ENTRY, List.of(
            new ImportField("code", "SKU ou código de barras", true, "sku", "codigo", "código", "ean", "codigo de barras"),
            new ImportField("quantity", "Quantidade (entrada)", true, "quantidade", "qtd", "entrada"),
            new ImportField("reason", "Motivo", false, "motivo", "observacao", "observação"))),

    CUSTOMERS(Permission.CUSTOMER_CREATE, List.of(
            new ImportField("name", "Nome", true, "nome", "cliente"),
            new ImportField("document", "CPF/CNPJ", false, "cpf", "cnpj", "documento"),
            new ImportField("phone", "Telefone", false, "telefone", "celular", "fone"),
            new ImportField("email", "E-mail", false, "email", "e-mail"),
            new ImportField("address", "Endereço", false, "endereco", "endereço"),
            new ImportField("notes", "Observações", false, "observacoes", "observações", "obs"))),

    SUPPLIERS(Permission.SUPPLIER_CREATE, List.of(
            new ImportField("legalName", "Razão social", true, "razao social", "razão social", "nome", "fornecedor"),
            new ImportField("tradeName", "Nome fantasia", false, "fantasia", "nome fantasia"),
            new ImportField("document", "CNPJ/CPF", false, "cnpj", "cpf", "documento"),
            new ImportField("phone", "Telefone", false, "telefone", "fone"),
            new ImportField("email", "E-mail", false, "email", "e-mail"),
            new ImportField("address", "Endereço", false, "endereco", "endereço"),
            new ImportField("notes", "Observações", false, "observacoes", "observações", "obs")));

    private final Permission requiredPermission;
    private final List<ImportField> fields;

    ImportEntityType(Permission requiredPermission, List<ImportField> fields) {
        this.requiredPermission = requiredPermission;
        this.fields = fields;
    }

    public Permission requiredPermission() {
        return requiredPermission;
    }

    public List<ImportField> fields() {
        return fields;
    }

    public record ImportField(String key, String label, boolean required, String... aliases) {
    }
}
