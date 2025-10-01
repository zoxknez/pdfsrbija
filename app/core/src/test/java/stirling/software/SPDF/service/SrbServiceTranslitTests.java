package stirling.software.SPDF.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SrbServiceTranslitTests {

    private final SrbService s = new SrbService();

    @Test
    void lat2cyr_basicDigraphs() {
        assertEquals("Џемпер Љиља", s.latinToCyrillic("Džemper Ljilja"));
        assertEquals("Њива", s.latinToCyrillic("Njiva"));
        assertEquals("ЏЕМПЕР ЉУБАВ", s.latinToCyrillic("DŽEMPER LJUBAV"));
    }

    @Test
    void cyr2lat_basicDigraphs() {
        assertEquals("Džemper Ljilja", s.cyrillicToLatin("Џемпер Љиља"));
        assertEquals("Njiva", s.cyrillicToLatin("Њива"));
        assertEquals("DŽEMPER LJUBAV", s.cyrillicToLatin("ЏЕМПЕР ЉУБАВ"));
    }

    @Test
    void roundtrip_preservesMeaning() {
        String src = "Džemper Ljilja – đak, ćumur, šuma, čarolija, žar.";
        String toCyr = s.latinToCyrillic(src);
        String back = s.cyrillicToLatin(toCyr);
        assertEquals("Džemper Ljilja – đak, ćumur, šuma, čarolija, žar.", back);
    }
}
