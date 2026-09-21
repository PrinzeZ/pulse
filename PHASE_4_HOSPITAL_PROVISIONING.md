# Phase 4 — Hospital Provisioning

## Implemented
- Public hospital registration.
- State/district selection from the existing `states` and `districts` tables.
- Email-verification state using a secure random verification token.
- District-scoped review: a district admin only sees verified registrations for their own district.
- District approval/rejection.
- Approval creates the hospital and a hospital-scoped `ADMIN` account.
- One-time administrator activation token.
- Initial password setup using BCrypt.
- Hospital receives the existing `district_id` scope used by the hierarchy.
- Existing login, staff, stock and Phase 3 accounts are preserved.

## Development-only email behavior
No SMTP dependency was added in this phase. Registration verification and administrator activation links are printed to the server console so the complete workflow can be tested locally.

A production email provider can be added later without changing the registration/provisioning data model.

## Workflow
1. Applicant opens `/hospital/register`.
2. Applicant submits hospital, email, administrator, state and district.
3. Registration is stored as `PENDING_EMAIL`.
4. Verification token is generated.
5. Applicant opens `/hospital/verify?token=...`.
6. Registration becomes `EMAIL_VERIFIED`.
7. The assigned district admin opens `/district-admin/hospital-registrations`.
8. District admin approves or rejects.
9. Approval creates the hospital and scoped hospital admin.
10. One-time setup token is generated.
11. Hospital admin sets the initial password.
12. Hospital admin can then use the normal `/login` flow.

## Deferred
- Real SMTP/email delivery.
- Maps/location selection.
- H2/offline integration.
- Railway/cloud synchronization.
- Tamper-evident audit logs.
- Dashboard visual redesign.
