# P.U.L.S.E Phase 4 UI / Workflow Notes

## Navigation
- Administrator sidebars persist across dashboard and Medicine Search.
- The menu button collapses/expands the sidebar.
- The collapsed state is remembered in browser localStorage.

## Tier presentation
- Hospital Admin: local operational view and local alerts.
- District Admin: district-scoped signals, requests, hospital approvals and verification desk.
- State Admin: statewide/district-wide signals and escalated request view.
- Each administrator header shows a verified tier badge.

## Alert visibility
- Hospital Admin continues to see detailed hospital alerts.
- District Admin does not receive every hospital alert. It receives aggregated medicine shortages and hospital-wide critical-stock signals.
- State Admin does not receive individual hospital alerts. It receives district-wide critical-stock signals and medicine shortages that affect every visible hospital.
- Request-generated alerts are intentionally left for Phase 7, when the request persistence/workflow is implemented.

## Requests
The tier-specific Requests pages are present as the navigation/UI foundation. They intentionally do not invent request records before Phase 7's request entity and workflow exist:
Hospital -> District -> State.
Only explicitly submitted/escalated requests should appear at each higher tier.

## Verifications
District Admin has a Verifications page showing registrations in the assigned district and their current status. State Admin has no verification page.
