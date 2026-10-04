package com.nexus.pdv.permission.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cargos padrão criados para cada tenant. TENANT_ADMIN é de sistema (imutável e com todas as
 * permissões); os demais são modelos iniciais que o administrador pode ajustar.
 */
public enum RoleCode {

    TENANT_ADMIN("Administrador", "Acesso total à empresa"),
    MANAGER("Gerente", "Gestão operacional da loja"),
    CASHIER("Caixa", "Operação da frente de caixa"),
    STOCK_OPERATOR("Estoquista", "Operação de estoque");

    private final String defaultName;
    private final String defaultDescription;

    RoleCode(String defaultName, String defaultDescription) {
        this.defaultName = defaultName;
        this.defaultDescription = defaultDescription;
    }

    public String defaultName() {
        return defaultName;
    }

    public String defaultDescription() {
        return defaultDescription;
    }

    public Set<Permission> defaultPermissions() {
        return switch (this) {
            case TENANT_ADMIN -> EnumSet.allOf(Permission.class);
            case MANAGER -> {
                Set<Permission> set = EnumSet.allOf(Permission.class);
                set.removeAll(EnumSet.of(Permission.USER_CREATE, Permission.USER_UPDATE, Permission.USER_DISABLE,
                        Permission.USER_PERMISSION_MANAGE, Permission.SETTINGS_MANAGE, Permission.IMPORT_EXECUTE));
                yield set;
            }
            case CASHIER -> EnumSet.of(Permission.PDV_ACCESS, Permission.SALE_CREATE, Permission.SALE_READ,
                    Permission.PRODUCT_READ, Permission.CUSTOMER_READ, Permission.CUSTOMER_CREATE);
            case STOCK_OPERATOR -> EnumSet.of(Permission.PRODUCT_READ, Permission.CATEGORY_READ, Permission.STOCK_READ,
                    Permission.STOCK_ENTRY, Permission.STOCK_MOVEMENT_READ);
        };
    }
}
