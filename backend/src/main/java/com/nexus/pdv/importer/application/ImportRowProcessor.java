package com.nexus.pdv.importer.application;

import com.nexus.pdv.category.domain.Category;
import com.nexus.pdv.category.infrastructure.CategoryRepository;
import com.nexus.pdv.customer.domain.Customer;
import com.nexus.pdv.customer.infrastructure.CustomerRepository;
import com.nexus.pdv.importer.domain.ImportEntityType;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.product.domain.Product;
import com.nexus.pdv.product.domain.ProductUnit;
import com.nexus.pdv.product.infrastructure.ProductRepository;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.text.Documents;
import com.nexus.pdv.shared.text.Texts;
import com.nexus.pdv.stock.application.StockService;
import com.nexus.pdv.stock.domain.StockMovementType;
import com.nexus.pdv.supplier.domain.Supplier;
import com.nexus.pdv.supplier.infrastructure.SupplierRepository;
import com.nexus.pdv.user.domain.User;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Regras de validação e aplicação de cada tipo de importação. */
@Component
public class ImportRowProcessor {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public enum Outcome { CREATED, UPDATED }

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final StockService stockService;

    public ImportRowProcessor(ProductRepository productRepository, CategoryRepository categoryRepository,
            CustomerRepository customerRepository, SupplierRepository supplierRepository, StockService stockService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.stockService = stockService;
    }

    /**
     * Valida uma linha. {@code seenKeys} detecta duplicidades dentro do próprio arquivo.
     *
     * @return lista de erros (vazia = linha válida)
     */
    public List<String> validate(ImportEntityType type, Map<String, String> row, AccessContext context,
            Set<String> seenKeys) {
        List<String> errors = new ArrayList<>();
        switch (type) {
            case PRODUCTS -> {
                requireText(row, "name", "Nome", 150, errors);
                maxLength(row, "sku", "SKU", 60, errors);
                maxLength(row, "barcode", "Código de barras", 60, errors);
                decimal(row, "salePrice", "Preço de venda", true, errors);
                decimal(row, "costPrice", "Preço de custo", false, errors);
                decimal(row, "minimumStock", "Estoque mínimo", false, errors);
                BigDecimal initial = decimal(row, "initialStock", "Estoque inicial", false, errors);
                ProductUnit unit = unit(row.get("unit"), errors);
                if (unit != null && unit.isDiscrete() && initial != null && initial.stripTrailingZeros().scale() > 0) {
                    errors.add("Estoque inicial deve ser inteiro para a unidade " + unit.name() + ".");
                }
                if (Texts.clean(row.get("costPrice")) != null && !context.hasPermission(Permission.PRODUCT_COST_VIEW)) {
                    errors.add("Sem permissão para importar preço de custo.");
                }
                String category = Texts.clean(row.get("category"));
                if (category != null && category.length() > 80) {
                    errors.add("Categoria: máximo de 80 caracteres.");
                } else if (category != null && categoryRepository.findByNameIgnoreCase(category).isEmpty()
                        && !context.hasPermission(Permission.CATEGORY_CREATE)) {
                    errors.add("Categoria \"" + category + "\" não existe e você não pode criá-la.");
                }
                duplicate(seenKeys, "sku", row.get("sku"), "SKU", errors);
                duplicate(seenKeys, "barcode", row.get("barcode"), "Código de barras", errors);
            }
            case CATEGORIES -> {
                requireText(row, "name", "Nome", 80, errors);
                maxLength(row, "description", "Descrição", 300, errors);
                duplicate(seenKeys, "name", row.get("name"), "Nome", errors);
            }
            case STOCK -> {
                String code = requireText(row, "code", "Código", 60, errors);
                BigDecimal quantity = decimal(row, "quantity", "Quantidade", true, errors);
                if (quantity != null && quantity.signum() <= 0) {
                    errors.add("Quantidade deve ser maior que zero.");
                }
                maxLength(row, "reason", "Motivo", 300, errors);
                if (code != null) {
                    Optional<Product> product = findProductByCode(code);
                    if (product.isEmpty()) {
                        errors.add("Produto com código \"" + code + "\" não encontrado.");
                    } else if (quantity != null && product.get().getUnit().isDiscrete()
                            && quantity.stripTrailingZeros().scale() > 0) {
                        errors.add("Quantidade deve ser inteira para \"" + product.get().getName() + "\".");
                    }
                }
            }
            case CUSTOMERS -> {
                requireText(row, "name", "Nome", 150, errors);
                document(row, errors);
                email(row, errors);
                maxLength(row, "phone", "Telefone", 30, errors);
                maxLength(row, "address", "Endereço", 300, errors);
                maxLength(row, "notes", "Observações", 1000, errors);
                duplicate(seenKeys, "document", Texts.digits(row.get("document")), "Documento", errors);
            }
            case SUPPLIERS -> {
                requireText(row, "legalName", "Razão social", 150, errors);
                maxLength(row, "tradeName", "Nome fantasia", 150, errors);
                document(row, errors);
                email(row, errors);
                maxLength(row, "phone", "Telefone", 30, errors);
                maxLength(row, "address", "Endereço", 300, errors);
                maxLength(row, "notes", "Observações", 1000, errors);
                duplicate(seenKeys, "document", Texts.digits(row.get("document")), "Documento", errors);
            }
        }
        return errors;
    }

    /** Quantos produtos NOVOS a linha criaria (para verificar o limite do plano antes de importar). */
    public boolean createsNewProduct(Map<String, String> row) {
        return findExistingProduct(row).isEmpty();
    }

    public Outcome apply(ImportEntityType type, Map<String, String> row, AccessContext context) {
        return switch (type) {
            case PRODUCTS -> applyProduct(row, context);
            case CATEGORIES -> applyCategory(row);
            case STOCK -> applyStock(row);
            case CUSTOMERS -> applyCustomer(row);
            case SUPPLIERS -> applySupplier(row);
        };
    }

    private Outcome applyProduct(Map<String, String> row, AccessContext context) {
        String categoryName = Texts.clean(row.get("category"));
        Category category = null;
        if (categoryName != null) {
            category = categoryRepository.findByNameIgnoreCase(categoryName)
                    .orElseGet(() -> categoryRepository.save(new Category(categoryName, null)));
        }
        ProductUnit unit = Optional.ofNullable(unit(row.get("unit"), new ArrayList<>())).orElse(ProductUnit.UN);
        BigDecimal salePrice = parseDecimal(row.get("salePrice"));
        BigDecimal costPrice = parseDecimal(row.get("costPrice"));
        BigDecimal minimum = parseDecimal(row.get("minimumStock"));
        String sku = Texts.clean(row.get("sku"));
        String barcode = Texts.clean(row.get("barcode"));
        Optional<Product> existing = findExistingProduct(row);
        if (existing.isPresent()) {
            Product product = existing.get();
            product.update(Texts.clean(row.get("name")), product.getDescription(), sku != null ? sku : product.getSku(),
                    barcode != null ? barcode : product.getBarcode(), category != null ? category : product.getCategory(),
                    product.getSupplier(), salePrice, minimum != null ? minimum : product.getMinimumStock(), unit);
            if (costPrice != null && context.hasPermission(Permission.PRODUCT_COST_VIEW)) {
                product.changeCostPrice(costPrice);
            }
            return Outcome.UPDATED;
        }
        Product product = productRepository.save(new Product(Texts.clean(row.get("name")), null, sku, barcode, category,
                null, salePrice, costPrice, minimum, unit));
        BigDecimal initial = parseDecimal(row.get("initialStock"));
        if (initial != null && initial.signum() > 0) {
            stockService.apply(product, StockMovementType.INITIAL, initial, "IMPORT", null, "Importação", true);
        }
        return Outcome.CREATED;
    }

    private Outcome applyCategory(Map<String, String> row) {
        String name = Texts.clean(row.get("name"));
        Optional<Category> existing = categoryRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            existing.get().update(existing.get().getName(), Texts.clean(row.get("description")));
            return Outcome.UPDATED;
        }
        categoryRepository.save(new Category(name, Texts.clean(row.get("description"))));
        return Outcome.CREATED;
    }

    private Outcome applyStock(Map<String, String> row) {
        Product product = findProductByCode(Texts.clean(row.get("code")))
                .flatMap(found -> productRepository.lockById(found.getId()))
                .orElseThrow(() -> new BusinessException(com.nexus.pdv.shared.error.ErrorCode.RESOURCE_NOT_FOUND,
                        "Produto não encontrado."));
        String reason = Texts.clean(row.get("reason"));
        stockService.apply(product, StockMovementType.ENTRY, parseDecimal(row.get("quantity")), "IMPORT", null,
                reason != null ? reason : "Importação", true);
        return Outcome.UPDATED;
    }

    private Outcome applyCustomer(Map<String, String> row) {
        String document = Documents.normalize(row.get("document"));
        String email = User.normalizeEmail(Texts.clean(row.get("email")));
        Optional<Customer> existing = document == null ? Optional.empty() : customerRepository.findFirstByDocument(document);
        if (existing.isPresent()) {
            existing.get().update(Texts.clean(row.get("name")), document, Texts.clean(row.get("phone")), email,
                    Texts.clean(row.get("address")), Texts.clean(row.get("notes")));
            return Outcome.UPDATED;
        }
        customerRepository.save(new Customer(Texts.clean(row.get("name")), document, Texts.clean(row.get("phone")), email,
                Texts.clean(row.get("address")), Texts.clean(row.get("notes"))));
        return Outcome.CREATED;
    }

    private Outcome applySupplier(Map<String, String> row) {
        String document = Documents.normalize(row.get("document"));
        String email = User.normalizeEmail(Texts.clean(row.get("email")));
        Optional<Supplier> existing = document == null ? Optional.empty() : supplierRepository.findFirstByDocument(document);
        if (existing.isPresent()) {
            existing.get().update(Texts.clean(row.get("legalName")), Texts.clean(row.get("tradeName")), document,
                    Texts.clean(row.get("phone")), email, Texts.clean(row.get("address")), Texts.clean(row.get("notes")));
            return Outcome.UPDATED;
        }
        supplierRepository.save(new Supplier(Texts.clean(row.get("legalName")), Texts.clean(row.get("tradeName")), document,
                Texts.clean(row.get("phone")), email, Texts.clean(row.get("address")), Texts.clean(row.get("notes"))));
        return Outcome.CREATED;
    }

    private Optional<Product> findExistingProduct(Map<String, String> row) {
        String sku = Texts.clean(row.get("sku"));
        String barcode = Texts.clean(row.get("barcode"));
        Optional<Product> bySku = sku == null ? Optional.empty() : productRepository.findFirstBySkuIgnoreCase(sku);
        return bySku.isPresent() || barcode == null ? bySku : productRepository.findFirstByBarcode(barcode);
    }

    private Optional<Product> findProductByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return productRepository.findFirstByBarcode(code).or(() -> productRepository.findFirstBySkuIgnoreCase(code));
    }

    // ---------- validações auxiliares ----------

    private static String requireText(Map<String, String> row, String key, String label, int max, List<String> errors) {
        String value = Texts.clean(row.get(key));
        if (value == null) {
            errors.add(label + " é obrigatório.");
            return null;
        }
        if (value.length() > max) {
            errors.add(label + ": máximo de " + max + " caracteres.");
        }
        return value;
    }

    private static void maxLength(Map<String, String> row, String key, String label, int max, List<String> errors) {
        String value = Texts.clean(row.get(key));
        if (value != null && value.length() > max) {
            errors.add(label + ": máximo de " + max + " caracteres.");
        }
    }

    private static BigDecimal decimal(Map<String, String> row, String key, String label, boolean required,
            List<String> errors) {
        String raw = Texts.clean(row.get(key));
        if (raw == null) {
            if (required) {
                errors.add(label + " é obrigatório.");
            }
            return null;
        }
        BigDecimal value = parseDecimal(raw);
        if (value == null) {
            errors.add(label + ": valor numérico inválido (\"" + raw + "\").");
        } else if (value.signum() < 0) {
            errors.add(label + " não pode ser negativo.");
        }
        return value;
    }

    private static ProductUnit unit(String raw, List<String> errors) {
        String value = Texts.clean(raw);
        if (value == null) {
            return ProductUnit.UN;
        }
        try {
            return ProductUnit.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add("Unidade inválida: " + value + " (use UN, CX, PCT, KG, G, L, ML ou M).");
            return null;
        }
    }

    private static void document(Map<String, String> row, List<String> errors) {
        String digits = Texts.digits(row.get("document"));
        if (digits != null && digits.length() != 11 && digits.length() != 14) {
            errors.add("Documento deve ter 11 (CPF) ou 14 (CNPJ) dígitos.");
        }
    }

    private static void email(Map<String, String> row, List<String> errors) {
        String value = Texts.clean(row.get("email"));
        if (value != null && (!EMAIL.matcher(value).matches() || value.length() > 254)) {
            errors.add("E-mail inválido.");
        }
    }

    private static void duplicate(Set<String> seen, String key, String value, String label, List<String> errors) {
        String cleaned = Texts.clean(value);
        if (cleaned == null) {
            return;
        }
        if (!seen.add(key + ":" + cleaned.toLowerCase(Locale.ROOT))) {
            errors.add(label + " \"" + cleaned + "\" repetido no arquivo.");
        }
    }

    /** Aceita "1.234,56", "1234,56", "1234.56" e "R$ 10,00". */
    static BigDecimal parseDecimal(String raw) {
        String value = Texts.clean(raw);
        if (value == null) {
            return null;
        }
        value = value.replace("R$", "").replace(" ", "");
        if (value.contains(",")) {
            value = value.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
