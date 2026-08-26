package com.redhat.migrationtoolkit.rhcl.controller;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.InetAddress;

/** DNS resolution check for Gateway external hostnames. */
@ApplicationScoped
public class GatewayDnsResolver {

    public boolean isResolvable(String hostname) {
        try {
            InetAddress.getByName(hostname);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
