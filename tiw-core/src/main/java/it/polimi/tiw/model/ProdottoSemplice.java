package it.polimi.tiw.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Rappresenta un prodotto semplice (foglia dell'albero).
 * Non contiene altri prodotti, ma ha una lista di varianti concrete (SKU).
 */
public class ProdottoSemplice extends Prodotto {
    private List<SKU> skus = new ArrayList<>();

    public ProdottoSemplice() {}

    public List<SKU> getSKUs() {
        return skus;
    }

    public void setSKUs(List<SKU> skus) {
        this.skus = skus;
    }

    public void addSKU(SKU sku) {
        this.skus.add(sku);
    }
}
