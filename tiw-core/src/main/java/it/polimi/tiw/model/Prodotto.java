package it.polimi.tiw.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * Rappresenta un prodotto generico nel sistema.
 * Classe base astratta per ProdottoComposto e ProdottoSemplice.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tipo")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProdottoComposto.class, name = "COMPOSTO"),
    @JsonSubTypes.Type(value = ProdottoSemplice.class, name = "SEMPLICE")
})
public abstract class Prodotto {

    private int id;
    private int codice;
    private String nome;
    private String tipo;        // "SEMPLICE" o "COMPOSTO"
    private String descrizione; // NULL per SEMPLICE
    private BigDecimal prezzoMin;
    private BigDecimal prezzoMax;
    private Integer idPadre;    // NULL = radice

    public Prodotto() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCodice() { return codice; }
    public void setCodice(int codice) { this.codice = codice; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }

    public BigDecimal getPrezzoMin() { return prezzoMin; }
    public void setPrezzoMin(BigDecimal prezzoMin) { this.prezzoMin = prezzoMin; }

    public BigDecimal getPrezzoMax() { return prezzoMax; }
    public void setPrezzoMax(BigDecimal prezzoMax) { this.prezzoMax = prezzoMax; }

    public Integer getIdPadre() { return idPadre; }
    public void setIdPadre(Integer idPadre) { this.idPadre = idPadre; }
}
