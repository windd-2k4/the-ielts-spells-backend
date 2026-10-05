package com.theieltsspells.billing.infrastructure.sepay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SepayEInvoiceClientXmlTest {

    @Test
    void extractsTaxAuthorityCodeFromInvoiceXml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <HDon><DLHDon><MCCQT>0070B54DAD8B0249E5B261CD8E66A19BF6</MCCQT></DLHDon></HDon>
                """;

        assertEquals(
                "0070B54DAD8B0249E5B261CD8E66A19BF6",
                SepayEInvoiceClient.extractTaxAuthorityCode(xml)
        );
    }

    @Test
    void supportsNamespacedTaxAuthorityCodeAndMissingValue() {
        assertEquals(
                "ABC123",
                SepayEInvoiceClient.extractTaxAuthorityCode("<inv:MCCQT xmlns:inv=\"urn:test\"> ABC123 </inv:MCCQT>")
        );
        assertNull(SepayEInvoiceClient.extractTaxAuthorityCode("<HDon><DLHDon /></HDon>"));
    }
}
