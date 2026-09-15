package com.pulse.service;

import com.pulse.dto.SearchResult;
import java.util.List;

public interface SearchService {
    List<SearchResult> search(String district, String medicineQuery);
}
