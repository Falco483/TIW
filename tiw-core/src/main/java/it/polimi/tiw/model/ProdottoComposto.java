package it.polimi.tiw.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Rappresenta un prodotto composto che può contenere altri prodotti (composti o semplici).
 * Implementa il nodo interno di una struttura gerarchica ad albero.
 */
public class ProdottoComposto extends Prodotto {
    private List<Prodotto> figli = new ArrayList<>();

    public ProdottoComposto() {}

    /**
     * Recupera la lista di prodotti figli (sotto-componenti).
     *
     * @return la lista dei figli.
     */
    public List<Prodotto> getFigli() {
        return figli;
    }

    /**
     * Imposta la lista di prodotti figli (sotto-componenti).
     *
     * @param figli la lista dei figli da impostare.
     */
    public void setFigli(List<Prodotto> figli) {
        this.figli = figli;
    }

    /**
     * Aggiunge un prodotto figlio (sotto-componente) all'albero.
     *
     * @param figlio il prodotto figlio da aggiungere.
     */
    public void addFiglio(Prodotto figlio) {
        this.figli.add(figlio);
    }
}
