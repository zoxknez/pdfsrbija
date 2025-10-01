package stirling.software.SPDF.service;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

@Service
public class SrbUblService {

    private static final String UBL_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CBC_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    public Map<String, Object> parseInvoiceSummary(byte[] xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

        XPathFactory xpf = XPathFactory.newInstance();
        XPath xp = xpf.newXPath();
        xp.setNamespaceContext(
                new NamespaceContext() {
                    public String getNamespaceURI(String prefix) {
                        return switch (prefix) {
                            case "ubl" -> UBL_NS;
                            case "cbc" -> CBC_NS;
                            case "cac" -> CAC_NS;
                            default -> XMLConstants.NULL_NS_URI;
                        };
                    }

                    public String getPrefix(String nsURI) {
                        return null;
                    }

                    public Iterator<String> getPrefixes(String nsURI) {
                        return null;
                    }
                });

        String id = (String) xp.evaluate("/ubl:Invoice/cbc:ID/text()", doc, XPathConstants.STRING);
        String issueDate =
                (String)
                        xp.evaluate(
                                "/ubl:Invoice/cbc:IssueDate/text()", doc, XPathConstants.STRING);
        String currency =
                (String)
                        xp.evaluate(
                                "/ubl:Invoice/*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount']/@currencyID",
                                doc,
                                XPathConstants.STRING);
        String amount =
                (String)
                        xp.evaluate(
                                "/ubl:Invoice/*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount']/text()",
                                doc,
                                XPathConstants.STRING);
        String supplier =
                (String)
                        xp.evaluate(
                                "/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyName/cbc:Name/text()",
                                doc,
                                XPathConstants.STRING);
        if (supplier == null || supplier.isBlank()) {
            supplier =
                    (String)
                            xp.evaluate(
                                    "/ubl:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName/text()",
                                    doc,
                                    XPathConstants.STRING);
        }
        String customer =
                (String)
                        xp.evaluate(
                                "/ubl:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyName/cbc:Name/text()",
                                doc,
                                XPathConstants.STRING);
        String iban =
                (String)
                        xp.evaluate(
                                "/ubl:Invoice/cac:PaymentMeans/cac:PayeeFinancialAccount/cbc:ID/text()",
                                doc,
                                XPathConstants.STRING);

        Map<String, Object> out = new HashMap<>();
        out.put("id", safe(id));
        out.put("issueDate", safe(issueDate));
        out.put("currency", safe(currency));
        out.put("amount", safe(amount));
        out.put("supplier", safe(supplier));
        out.put("customer", safe(customer));
        out.put("iban", safe(iban));
        return out;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
