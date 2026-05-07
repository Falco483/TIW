package it.polimi.tiw.model;

import java.util.ArrayList;
import java.util.List;

public class ProdottoComposto extends Prodotto {
    private List<Prodotto> figli = new ArrayList<>();

    public ProdottoComposto() {}

    public List<Prodotto> getFigli() {
        return figli;
    }

    public void setFigli(List<Prodotto> figli) {
        this.figli = figli;
    }

    public void addFiglio(Prodotto figlio) {
        this.figli.add(figlio);
    }
}
