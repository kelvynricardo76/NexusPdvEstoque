package com.nexus.pdv.product.domain;

/** Unidades de venda. Unidades discretas exigem quantidades inteiras. */
public enum ProductUnit {
    UN(true),
    CX(true),
    PCT(true),
    KG(false),
    G(false),
    L(false),
    ML(false),
    M(false);

    private final boolean discrete;

    ProductUnit(boolean discrete) {
        this.discrete = discrete;
    }

    public boolean isDiscrete() {
        return discrete;
    }
}
