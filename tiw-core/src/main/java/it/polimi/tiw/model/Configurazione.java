package it.polimi.tiw.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Configurazione {
    private int id;
    private String clienteUsername;
    private int prodottoRadiceId;
    private String nome;
    private LocalDateTime dataCreazione;
    private LocalDateTime dataModifica;
    private BigDecimal prezzoTotale;

    public Configurazione() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getClienteUsername() { return clienteUsername; }
    public void setClienteUsername(String clienteUsername) { this.clienteUsername = clienteUsername; }

    public int getProdottoRadiceId() { return prodottoRadiceId; }
    public void setProdottoRadiceId(int prodottoRadiceId) { this.prodottoRadiceId = prodottoRadiceId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public LocalDateTime getDataCreazione() { return dataCreazione; }
    public void setDataCreazione(LocalDateTime dataCreazione) { this.dataCreazione = dataCreazione; }

    public LocalDateTime getDataModifica() { return dataModifica; }
    public void setDataModifica(LocalDateTime dataModifica) { this.dataModifica = dataModifica; }

    public BigDecimal getPrezzoTotale() { return prezzoTotale; }
    public void setPrezzoTotale(BigDecimal prezzoTotale) { this.prezzoTotale = prezzoTotale; }
}