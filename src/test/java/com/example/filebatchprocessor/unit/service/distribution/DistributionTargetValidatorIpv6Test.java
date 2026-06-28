package com.example.filebatchprocessor.unit.service.distribution;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.service.distribution.DistributionTargetValidator;
import org.junit.jupiter.api.Test;

/**
 * IPv6 SSRF 覆盖回归:block-internal 开启时,ULA / IPv4-mapped / link-local 内网 IPv6 必须拦截,
 * 公网 IPv6 必须放行。用 IPv6 字面量 host 避免依赖外部 DNS。
 */
class DistributionTargetValidatorIpv6Test {

    private final DistributionTargetValidator validator = new DistributionTargetValidator("", true);

    @Test
    void blocksUlaIpv6() {
        assertThatThrownBy(() -> validator.validate("http://[fc00::1]/x")).isInstanceOf(BusinessException.class);
    }

    @Test
    void blocksLinkLocalIpv6() {
        assertThatThrownBy(() -> validator.validate("http://[fe80::1]/x")).isInstanceOf(BusinessException.class);
    }

    @Test
    void blocksIpv4MappedPrivate() {
        assertThatThrownBy(() -> validator.validate("http://[::ffff:10.0.0.1]/x"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void blocksIpv4MappedMetadata() {
        assertThatThrownBy(() -> validator.validate("http://[::ffff:169.254.169.254]/x"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void allowsPublicIpv6() {
        // Cloudflare 公网 DNS,放行
        assertThatCode(() -> validator.validate("http://[2606:4700:4700::1111]/x"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksPrivateIpv4Regression() {
        assertThatThrownBy(() -> validator.validate("http://10.1.2.3/x")).isInstanceOf(BusinessException.class);
    }

    @Test
    void defaultPolicyBlocksCloudMetadataAddress() {
        DistributionTargetValidator defaultValidator = new DistributionTargetValidator("");

        assertThatThrownBy(() -> defaultValidator.validate("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void allowsPublicIpv4Regression() {
        // example.com 的公网 IP,放行
        assertThatCode(() -> validator.validate("http://93.184.216.34/x")).doesNotThrowAnyException();
    }
}
