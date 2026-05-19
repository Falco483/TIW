package it.polimi.tiw.model;

import java.math.BigDecimal;

public class ElementoCatalogo {
    private int id;
    private int codice;
    private String nome;
    private String descrizione;
    private BigDecimal prezzoMin;
    private BigDecimal prezzoMax;
    private String tipo; // "SKU", "SEMPLICE", "COMPOSTO"

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCodice() { return codice; }
    public void setCodice(int codice) { this.codice = codice; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }

    public BigDecimal getPrezzoMin() { return prezzoMin; }
    public void setPrezzoMin(BigDecimal prezzoMin) { this.prezzoMin = prezzoMin; }

    public BigDecimal getPrezzoMax() { return prezzoMax; }
    public void setPrezzoMax(BigDecimal prezzoMax) { this.prezzoMax = prezzoMax; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}
