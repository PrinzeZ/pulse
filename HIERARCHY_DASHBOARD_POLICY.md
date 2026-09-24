# P.U.L.S.E Hierarchy Dashboard Policy

## District administrator
The district dashboard is an exception dashboard, not a hospital inventory table.

A hospital is shown as a RED district-level exception only when every tracked medicine line represented for that hospital is at or below its critical threshold. Individual medicine shortages remain at hospital level.

## State administrator
The state dashboard is also an exception dashboard.

A district is shown as RED when at least 50% of the hospitals in that district are hospital-wide critical. The dashboard shows the district exception and the count of critical hospitals, rather than individual hospital/medicine rows.

## Offline behavior
The hierarchy aggregation uses the existing local H2/offline store fallback. District/state exception signals are calculated from local stock data as well as cloud stock data, so the high-level dashboards do not lose their alerts when the cloud database is temporarily unavailable.

Map tiles and external routing remain internet-dependent by nature; the core local application, authentication, inventory and hierarchy dashboards are designed to continue working from the local node.
