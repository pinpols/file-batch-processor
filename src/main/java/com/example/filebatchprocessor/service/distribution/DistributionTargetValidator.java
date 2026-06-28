package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 文件分发目标地址校验,防 SSRF / 内网外泄。
 *
 * <ul>
 *   <li>配置 {@code distribution.allowed-hosts} 后:目标 host 必须命中白名单(精确或后缀匹配),否则拒绝——这是主控制;
 *   <li>{@code distribution.block-internal-targets=true} 时额外拦截环回 / 链路本地(含 169.254.169.254 云元数据)/
 *       私网 / 站点本地地址。默认 false,因为向内网主机分发文件是合法主用途;在不可信网络环境再开启。
 * </ul>
 */
@Slf4j
@Component
public class DistributionTargetValidator {

    private final List<String> allowedHosts;
    private final boolean blockInternal;

    @org.springframework.beans.factory.annotation.Autowired
    public DistributionTargetValidator(
            @Value("${distribution.allowed-hosts:}") String allowedHostsCsv,
            @Value("${distribution.block-internal-targets:false}") boolean blockInternal) {
        this.allowedHosts = StringUtils.hasText(allowedHostsCsv)
                ? Arrays.stream(allowedHostsCsv.split(","))
                        .map(String::trim)
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .filter(s -> !s.isEmpty())
                        .toList()
                : List.of();
        this.blockInternal = blockInternal;
    }

    /** 便捷构造:仅白名单、不拦内网(测试/内网部署默认)。 */
    public DistributionTargetValidator(String allowedHostsCsv) {
        this(allowedHostsCsv, false);
    }

    /** 校验 URL/地址,不通过则抛 BusinessException(INVALID_ARGUMENT)。 */
    public void validate(String target) {
        if (!StringUtils.hasText(target)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "distribution target is required");
        }
        URI uri;
        try {
            uri = URI.create(target.contains("://") ? target : "//" + target);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid distribution target: " + target);
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "distribution target has no host: " + target);
        }
        String hostLower = host.toLowerCase(Locale.ROOT);

        if (!allowedHosts.isEmpty()) {
            boolean ok = allowedHosts.stream().anyMatch(a -> hostLower.equals(a) || hostLower.endsWith("." + a));
            if (!ok) {
                throw new BusinessException(
                        ErrorCode.INVALID_ARGUMENT, "distribution target host not in allow-list: " + host);
            }
        }

        if (!blockInternal) {
            return;
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isAnyLocalAddress()
                        || isPrivateOrMetadata(addr)) {
                    throw new BusinessException(
                            ErrorCode.INVALID_ARGUMENT,
                            "distribution target resolves to a non-routable/internal address: " + host + " -> " + addr);
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "cannot resolve distribution target host: " + host);
        }
    }

    private boolean isPrivateOrMetadata(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            return isPrivateIpv4(b[0] & 0xff, b[1] & 0xff);
        }
        if (b.length == 16) {
            // IPv4-mapped IPv6(::ffff:a.b.c.d):前 10 字节 0,11-12 字节 0xff → 按末 4 字节复用 IPv4 规则
            boolean ipv4Mapped = (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
            for (int i = 0; i < 10 && ipv4Mapped; i++) {
                if (b[i] != 0) {
                    ipv4Mapped = false;
                }
            }
            if (ipv4Mapped) {
                return isPrivateIpv4(b[12] & 0xff, b[13] & 0xff);
            }
            // ULA fc00::/7
            if ((b[0] & 0xfe) == 0xfc) return true;
            // IPv6 link-local fe80::/10(isLinkLocalAddress 已覆盖,冗余兜底)
            if ((b[0] & 0xff) == 0xfe && (b[1] & 0xc0) == 0x80) return true;
        }
        return false;
    }

    /** IPv4 私网/元数据判定:10/8、172.16-31、192.168/16、100.64-127(CGNAT)、169.254/16(含元数据)。 */
    private boolean isPrivateIpv4(int o0, int o1) {
        if (o0 == 10) return true;
        if (o0 == 172 && o1 >= 16 && o1 <= 31) return true;
        if (o0 == 192 && o1 == 168) return true;
        if (o0 == 100 && o1 >= 64 && o1 <= 127) return true;
        if (o0 == 169 && o1 == 254) return true;
        return false;
    }
}
