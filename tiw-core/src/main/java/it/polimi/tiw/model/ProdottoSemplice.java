package it.polimi.tiw.model;

import java.util.ArrayList;
import java.util.List;

public class ProdottoSemplice extends Prodotto {
    private List<Sku> skus = new ArrayList<>();

    public ProdottoSemplice() {}

    public List<Sku> getSkus() {
        return skus;
    }

    public void setSkus(List<Sku> skus) {
        this.skus = skus;
    }

    public void addSku(Sku sku) {
        this.skus.add(sku);
    }
}
