package it.polimi.tiw.utils;

public enum UserRole {
    CLIENTE,
    FORNITORE;

    /**
     * Converte la stringa letta dal DB (colonna `ruolo`) nel corrispondente enum.
     * Usare questo metodo nel LoginServlet/UtenteDAO quando si costruisce UtenteSessionDTO.
     *
     * @throws IllegalArgumentException se il valore non corrisponde a nessun ruolo noto
     */
    public static UserRole fromString(String value) throws IllegalArgumentException {
        if (value == null) throw new IllegalArgumentException("Role value is null");
        switch (value.toLowerCase()) {
            case "cliente", "client", "customer" -> {
                return CLIENTE;
            }
            case "fornitore", "supplier", "vendor" -> {
                return FORNITORE;
            }
            default -> throw new IllegalArgumentException("Unknown role: " + value);
        }
    }

    /** Path prefix protetto per questo ruolo (es. /fornitore/, /cliente/). */
    public String getPathPrefix() {
        return "/" + name().toLowerCase() + "/";
    }

    /** Path prefix dell'altro ruolo — accesso vietato. */
    public String getForbiddenPathPrefix() {
        return this == FORNITORE ? CLIENTE.getPathPrefix() : FORNITORE.getPathPrefix();
    }
}
