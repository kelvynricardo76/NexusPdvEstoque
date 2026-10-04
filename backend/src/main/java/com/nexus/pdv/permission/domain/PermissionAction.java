package com.nexus.pdv.permission.domain;

/** Coluna da matriz de permissões (VER / CRIAR / EDITAR / EXCLUIR) ou operação específica. */
public enum PermissionAction {
    VIEW,
    CREATE,
    UPDATE,
    DELETE,
    SPECIAL
}
