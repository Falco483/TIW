package it.polimi.tiw.dto;

import it.polimi.tiw.model.SKU;

import java.math.BigDecimal;

public class VoceConfigurazioneDTO {
    private final SKU sku;
    private final BigDecimal prezzoCongelato;

    public VoceConfigurazioneDTO(SKU sku, BigDecimal prezzoCongelato) {
        this.sku = sku;
        this.prezzoCongelato = prezzoCongelato;
    }

    public SKU getSku() {
        return sku;
    }

    public BigDecimal getPrezzoCongelato() {
        return prezzoCongelato;
    }
}
