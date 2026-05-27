package it.polimi.tiw.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ProdottoDAOTest {

    private Connection connection;
    private ProdottoDAO dao;

    @BeforeEach
    void setUp() throws SQLException, ClassNotFoundException {
        // Connessione diretta per i test locali
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/tiw_db", "root", "root1234");
        // Disabilitiamo l'autocommit per poter fare rollback alla fine di ogni test
        connection.setAutoCommit(false);
        dao = new ProdottoDAO(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            // Effettuiamo sempre rollback per lasciare il database pulito
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void testVerificaAciclicita() throws SQLException {
        // Creiamo tre prodotti composti
        int id1 = dao.insertComposto("10001", "Nodo 1", "Desc 1", null, null);
        int id2 = dao.insertComposto("10002", "Nodo 2", "Desc 2", null, null);
        int id3 = dao.insertComposto("10003", "Nodo 3", "Desc 3", null, null);

        // Creiamo la catena: 1 -> 2 -> 3
        dao.addFiglio(id1, id2);
        dao.addFiglio(id2, id3);

        // Verifichiamo che aggiungere 3 sotto 1 sia lecito (non crea cicli)
        assertTrue(dao.verificaAciclicita(id1, id3), "1 -> 3 non dovrebbe creare cicli");

        // Verifichiamo che aggiungere 1 sotto 3 CREA UN CICLO (1 -> 2 -> 3 -> 1)
        assertFalse(dao.verificaAciclicita(id3, id1), "3 -> 1 DEVE creare un ciclo");
    }

    @Test
    void testCalcolaProfondita() throws SQLException {
        // Creiamo una catena di 4 nodi
        int id1 = dao.insertComposto("20001", "L1", "Desc", null, null);
        int id2 = dao.insertComposto("20002", "L2", "Desc", null, null);
        int id3 = dao.insertComposto("20003", "L3", "Desc", null, null);
        int id4 = dao.insertComposto("20004", "L4", "Desc", null, null);

        dao.addFiglio(id1, id2);
        dao.addFiglio(id2, id3);
        dao.addFiglio(id3, id4);

        // La profondità del nodo radice (id1) dovrebbe essere 4
        assertEquals(4, dao.calcolaProfondita(id1), "La profondità dell'albero a partire da L1 deve essere 4");

        // La profondità di id2 dovrebbe essere 3
        assertEquals(3, dao.calcolaProfondita(id2), "La profondità dell'albero a partire da L2 deve essere 3");

        // La profondità del nodo foglia (id4) dovrebbe essere 1
        assertEquals(1, dao.calcolaProfondita(id4), "La profondità di un nodo foglia deve essere 1");
    }

    @Test
    void testContaSkuAssociate() throws SQLException {
        // Creiamo un prodotto semplice e due SKU
        int idSemplice = dao.insertSemplice("30001", "Semplice Test");
        
        SKUDAO skuDao = new SKUDAO(connection);
        it.polimi.tiw.model.SKU s1 = new it.polimi.tiw.model.SKU();
        s1.setCodice(40001);
        s1.setNome("SKU 1");
        s1.setPrezzo(new java.math.BigDecimal("10.00"));
        s1 = skuDao.insert(s1);

        it.polimi.tiw.model.SKU s2 = new it.polimi.tiw.model.SKU();
        s2.setCodice(40002);
        s2.setNome("SKU 2");
        s2.setPrezzo(new java.math.BigDecimal("20.00"));
        s2 = skuDao.insert(s2);

        // Inizialmente 0 SKU
        assertEquals(0, dao.contaSkuAssociate(idSemplice));

        // Aggiungiamo le SKU
        dao.addSku(idSemplice, s1.getId());
        assertEquals(1, dao.contaSkuAssociate(idSemplice));

        dao.addSku(idSemplice, s2.getId());
        assertEquals(2, dao.contaSkuAssociate(idSemplice));
    }
}
