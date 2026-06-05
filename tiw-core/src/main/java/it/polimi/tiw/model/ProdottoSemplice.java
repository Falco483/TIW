package it.polimi.tiw.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Rappresenta un prodotto semplice (foglia dell'albero).
 * Non contiene altri prodotti, ma ha una lista di varianti concrete (SKU).
 */
public class ProdottoSemplice extends Prodotto {
    private List<SKU> skus = new ArrayList<>();

    public ProdottoSemplice() {}

    /**
     * Recupera la lista di varianti SKU associate a questo prodotto semplice.
     *
     * @return la lista delle varianti SKU.
     */
    @JsonProperty("skus")
    public List<SKU> getSKUs() {
        return skus;
    }

    /**
     * Imposta la lista di varianti SKU associate a questo prodotto semplice.
     *
     * @param skus la lista delle varianti SKU.
     */
    @JsonProperty("skus")
    public void setSKUs(List<SKU> skus) {
        this.skus = skus;
    }

    /**
     * Aggiunge una variante SKU all'elenco delle varianti disponibili.
     *
     * @param sku la variante SKU da aggiungere.
     */
    public void addSKU(SKU sku) {
        this.skus.add(sku);
    }
}
