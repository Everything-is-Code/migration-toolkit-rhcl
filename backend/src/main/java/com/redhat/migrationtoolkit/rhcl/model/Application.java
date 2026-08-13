package com.redhat.migrationtoolkit.rhcl.model;

import java.util.List;

/**
 * 3scale Application (App ID / App Key credentials) fetched from the Admin API.
 */
public class Application {
    public String id;
    public String name;
    /** Application identifier used as App ID. */
    public String appId;
    /** Application keys from /applications/{id}/keys.json (primary first). */
    public List<String> keys;
}
