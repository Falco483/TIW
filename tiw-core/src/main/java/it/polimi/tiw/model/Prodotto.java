package it.polimi.tiw.model;

import java.math.BigDecimal;

/**
 * POJO per un Prodotto. DTO puro: solo dati, nessuna logica.
 * Usato sia dai Controller Thymeleaf sia dai Controller API.
 */
public abstract class Prodotto {

    private int id;
    private String codice;
    private String nome;
    private String tipo;        // "SEMPLICE" o "COMPOSTO"
    private String descrizione; // NULL per SEMPLICE
    private BigDecimal prezzoMin;
    private BigDecimal prezzoMax;
    private Integer idPadre;    // NULL = radice

    public Prodotto() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCodice() { return codice; }
    public void setCodice(String codice) { this.codice = codice; }

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
