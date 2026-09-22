package com.pulse.config;

import com.pulse.local.service.LocalOfflineStore;
import com.pulse.repository.AlertRepository;
import com.pulse.repository.HospitalRepository;
import com.pulse.repository.MedicineRepository;
import com.pulse.repository.StockEntryRepository;
import com.pulse.repository.UserRepository;
import com.pulse.service.AlertService;
import com.pulse.service.HierarchyDashboardService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly registers the hierarchy dashboard service.
 *
 * Keeping this bean definition here avoids component-scanning ambiguity while
 * the application has separate local and PostgreSQL persistence contexts.
 */
@Configuration
public class HierarchyDashboardConfig {

    @Bean
    public HierarchyDashboardService hierarchyDashboardService(
            AlertRepository alerts,
            HospitalRepository hospitals,
            MedicineRepository medicines,
            StockEntryRepository stock,
            UserRepository users,
            ObjectProvider<LocalOfflineStore> localStoreProvider,
            AlertService alertService) {
        return new HierarchyDashboardService(
                alerts, hospitals, medicines, stock, users, localStoreProvider, alertService);
    }
}
