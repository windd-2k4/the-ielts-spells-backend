package com.theieltsspells.billing.application;

import com.theieltsspells.billing.application.dto.InvoiceAdminDto;
import com.theieltsspells.billing.application.dto.ManualInvoiceRequest;
import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.InvoiceBuyerType;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualInvoiceApplicationServiceTests {

    @Test
    void createsHistoricalTransactionAndDraftWithRequiredBuyerData() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        ElectronicInvoiceRepository invoices = mock(ElectronicInvoiceRepository.class);
        ElectronicInvoiceService invoiceService = mock(ElectronicInvoiceService.class);
        when(transactions.save(any())).thenAnswer(invocation -> {
            var transaction = invocation.getArgument(0, com.theieltsspells.billing.domain.PaymentTransaction.class);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        ElectronicInvoice draft = new ElectronicInvoice();
        draft.setId(UUID.randomUUID());
        when(invoiceService.initOrGetInvoice(any(), any())).thenReturn(draft);
        when(invoices.save(any(ElectronicInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InvoiceAdminDto response = mock(InvoiceAdminDto.class);
        when(invoiceService.getInvoiceAdmin(draft.getId())).thenReturn(response);

        ManualInvoiceApplicationService service = new ManualInvoiceApplicationService(transactions, invoices, invoiceService);
        InvoiceAdminDto result = service.createDraft(new ManualInvoiceRequest(
                "Nguyễn Hoàng Phong",
                new BigDecimal("2000000"),
                LocalDate.of(2026, 10, 14),
                InvoiceBuyerType.PERSONAL,
                null,
                null,
                null,
                null,
                "Giao dịch cũ"
        ), "admin");

        assertThat(result).isSameAs(response);
        verify(transactions).save(any());
        verify(invoices).save(draft);
    }

    @Test
    void requiresCompanyAndTaxCodeForBusinessBuyer() {
        ManualInvoiceApplicationService service = new ManualInvoiceApplicationService(
                mock(PaymentTransactionRepository.class),
                mock(ElectronicInvoiceRepository.class),
                mock(ElectronicInvoiceService.class)
        );

        assertThatThrownBy(() -> service.createDraft(new ManualInvoiceRequest(
                "Công ty A",
                new BigDecimal("2000000"),
                LocalDate.of(2026, 10, 14),
                InvoiceBuyerType.BUSINESS,
                null,
                "",
                "",
                null,
                null
        ), "admin")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Tên doanh nghiệp");
    }
}
