package com.theieltsspells.billing.infrastructure.sepay;

import com.theieltsspells.billing.domain.BillingSetting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SepayEInvoiceClientConnectionTest {

    @Test
    void explainsWhenSandboxCredentialsWorkButNoProviderIsAttached() {
        SepayEInvoiceClient client = mock(SepayEInvoiceClient.class, CALLS_REAL_METHODS);
        BillingSetting setting = new BillingSetting();
        doReturn(List.of()).when(client).getProviderAccounts(setting);

        SepayEInvoiceClient.ConnectionTestResult result = client.testConnection(setting);

        assertThat(result.success()).isFalse();
        assertThat(result.message())
                .contains("Client ID/Secret Sandbox đã xác thực thành công")
                .contains("chưa được gắn Provider Account Sandbox")
                .contains("Không sử dụng UUID ví dụ");
    }
}
