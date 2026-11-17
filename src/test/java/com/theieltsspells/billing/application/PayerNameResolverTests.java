package com.theieltsspells.billing.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayerNameResolverTests {

    @Test
    @DisplayName("Tên từ đơn hàng luôn được ưu tiên")
    void authoritativeOrderNameWins() {
        PayerNameResolver.Resolution result = PayerNameResolver.resolve(
                "TKP#QRPSEP1ZZZZ58800105#000000.101126.000000.QRP",
                "Nguyễn Văn An",
                "KH2611101234"
        );

        assertThat(result.name()).isEqualTo("Nguyễn Văn An");
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.source()).isEqualTo(PayerNameResolver.Source.ORDER);
    }

    @Test
    @DisplayName("TKP chuẩn lấy đúng tên người chuyển")
    void parsesTkpName() {
        PayerNameResolver.Resolution result = PayerNameResolver.resolve(
                "TKP#QRPSEP1ZZZZ58800105#000000.101126.000000.PHAM VAN DUAN chuyen tien",
                null,
                null
        );

        assertThat(result.name()).isEqualTo("PHAM VAN DUAN");
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.source()).isEqualTo(PayerNameResolver.Source.TKP);
    }

    @Test
    @DisplayName("Viettel Money bỏ phần mô tả hệ thống")
    void parsesViettelMoneyName() {
        PayerNameResolver.Resolution result = PayerNameResolver.resolve(
                "TKP#QRPSEP1ZZZZ58800105#000000.101126.000000.Nguyen Ngoc Huyen chuyen tien tu ViettelMoney",
                null,
                null
        );

        assertThat(result.name()).isEqualTo("Nguyen Ngoc Huyen");
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.source()).isEqualTo(PayerNameResolver.Source.VIETTEL_MONEY);
    }

    @Test
    @DisplayName("MBVCB bỏ tiền tố ngân hàng và phần CT từ")
    void parsesMbvcbName() {
        PayerNameResolver.Resolution result = PayerNameResolver.resolve(
                "MBVCB.16868244359.Tran Vo Ba Vuong Lop LR.CT tu 0041000153627 VO THI HONG HOA toi",
                null,
                null
        );

        assertThat(result.name()).isEqualTo("Tran Vo Ba Vuong Lop LR");
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.source()).isEqualTo(PayerNameResolver.Source.MBVCB);
    }

    @Test
    @DisplayName("MoMo lấy tên từ metadata giao dịch")
    void parsesMomoName() {
        PayerNameResolver.Resolution result = PayerNameResolver.resolve(
                "TKP#QRPSEP1ZZZZ58800105#6307MOMOM2YC3ZG8.Test.20261103.184922.0382715051.NGUYEN THI LINH THUY.971025",
                null,
                null
        );

        assertThat(result.name()).isEqualTo("NGUYEN THI LINH THUY");
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.source()).isEqualTo(PayerNameResolver.Source.MOMO);
    }

    @Test
    @DisplayName("QR có hai tên chỉ giữ tên đầu và bắt buộc đối soát")
    void ambiguousQrRequiresReview() {
        PayerNameResolver.Resolution result = PayerNameResolver.resolve(
                "TKP#QRPSEP1ZZZZ58800105#020097041511051628572026CWDU657915.32759.162846.QR - Bui Thoa chuyen em Nguyen Thu Huong nop tien hoc",
                null,
                null
        );

        assertThat(result.name()).isEqualTo("Bui Thoa");
        assertThat(result.requiresReview()).isTrue();
        assertThat(result.reason()).contains("nhiều tên");
    }

    @Test
    @DisplayName("Tên chỉ là token QRP không được tự phát hành")
    void qrpOnlyFallsBackToReview() {
        PayerNameResolver.Resolution result = PayerNameResolver.resolve("QRP", null, null);

        assertThat(result.name()).isEqualTo("Người nộp học phí");
        assertThat(result.requiresReview()).isTrue();
    }
}
