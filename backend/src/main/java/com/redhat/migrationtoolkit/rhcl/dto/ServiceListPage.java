package com.redhat.migrationtoolkit.rhcl.dto;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of 3scale services for the selection UI.
 * {@code hasMore} is true when the Admin API returned a full page (another page may exist).
 * {@code total} is only set when known (last page); otherwise null.
 */
public class ServiceListPage {
    public List<ApiService> items = new ArrayList<>();
    public int page = 1;
    public int perPage = 20;
    public boolean hasMore;
    /** Known total when on the last page; null when more pages may exist. */
    public Integer total;
}
