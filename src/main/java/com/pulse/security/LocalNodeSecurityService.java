package com.pulse.security;

import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Network gate for the hospital-local credential mirror.
 *
 * This is a defense-in-depth boundary only. It never grants application
 * authorization; Spring Security role/scope checks remain authoritative.
 */
@Service
public class LocalNodeSecurityService {

    public boolean isTrustedLocalAddress(String address) {
        if (address == null || address.isBlank()) return false;
        try {
            InetAddress ip = InetAddress.getByName(address);
            if (ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress()) return true;
            byte[] raw = ip.getAddress();
            // IPv6 Unique Local Address range fc00::/7.
            return raw.length == 16 && (raw[0] & 0xFE) == 0xFC;
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
