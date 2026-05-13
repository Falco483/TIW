package it.polimi.tiw.model;

import java.math.BigDecimal;

/**
 * Rappresenta una SKU (Stock Keeping Unit), ovvero una variante concreta di un prodotto semplice.
 */
public class SKU {
    private int id;
    private int codice;
    private String nome;
    private String fotografia;
    private String descrizioneTecnica;
    private BigDecimal prezzo;

    public SKU() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCodice() {
        return codice;
    }

    public void setCodice(int codice) {
        this.codice = codice;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getFotografia() {
        return fotografia;
    }

    public void setFotografia(String fotografia) {
        this.fotografia = fotografia;
    }

    public String getDescrizioneTecnica() {
        return descrizioneTecnica;
    }

    public void setDescrizioneTecnica(String descrizioneTecnica) {
        this.descrizioneTecnica = descrizioneTecnica;
    }

    public BigDecimal getPrezzo() {
        return prezzo;
    }

    public void setPrezzo(BigDecimal prezzo) {
        this.prezzo = prezzo;
    }
}
