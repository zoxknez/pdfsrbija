package stirling.software.SPDF.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SrbServiceTest {

    private final SrbService srb = new SrbService();

    @Test
    void testIbanRsValidationAndFormatting() {
        String iban = "RS35260005601001611379"; // primer validnog RS IBAN-a
        assertTrue(srb.isValidIban(iban));
        assertTrue(srb.isValidIbanRS(iban));
        String pretty = srb.formatIbanPretty(iban);
        assertTrue(pretty.contains(" ")); // grupisanje po 4
        assertEquals(22, iban.length());
    }

    @Test
    void testModel97GenerateAndValidate() {
        var res = srb.generateModel97("123456");
        assertEquals("123456", res.get("base"));
        assertEquals(2, res.get("control").length());
        assertTrue(srb.validateModel97(res.get("reference")));
    }

    @Test
    void testTransliterationRoundtrip() {
        String lat = "Džungla Ljiljana i Njegoša";
        String cyr = srb.latinToCyrillic(lat);
        String back = srb.cyrillicToLatin(cyr);
        assertEquals(lat, back);
    }

    @Test
    void testIpsQrPayloadBasic() {
        String payload =
                srb.buildIpsQrPayload(
                        "RS35260005601001611379",
                        "Pera Perić",
                        "100.00",
                        "RSD",
                        "Uplata",
                        "97",
                        "12345678");
        assertTrue(payload.startsWith("K:PR|V:01|C:SRB"));
        assertTrue(payload.contains("|CUR:RSD"));
    }
}
