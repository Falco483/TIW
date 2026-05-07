package it.polimi.tiw.dto;

import java.math.BigDecimal;

public class DettaglioDTO {
    private int idConfigurazione;
    private int idProdotto;
    private int idSku;
    private BigDecimal prezzoUnitarioCongelato;

    public DettaglioDTO() {}

    public DettaglioDTO(int idProdotto, int idSku, BigDecimal prezzoUnitarioCongelato) {
        this.idProdotto = idProdotto;
        this.idSku = idSku;
        this.prezzoUnitarioCongelato = prezzoUnitarioCongelato;
    }

    public int getIdConfigurazione() { return idConfigurazione; }
    public void setIdConfigurazione(int idConfigurazione) { this.idConfigurazione = idConfigurazione; }

    public int getIdProdotto() { return idProdotto; }
    public void setIdProdotto(int idProdotto) { this.idProdotto = idProdotto; }

    public int getIdSku() { return idSku; }
    public void setIdSku(int idSku) { this.idSku = idSku; }

    public BigDecimal getPrezzoUnitarioCongelato() { return prezzoUnitarioCongelato; }
    public void setPrezzoUnitarioCongelato(BigDecimal prezzoUnitarioCongelato) { this.prezzoUnitarioCongelato = prezzoUnitarioCongelato; }
}
