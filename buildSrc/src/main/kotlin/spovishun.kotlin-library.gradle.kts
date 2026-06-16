// Convention for library modules (:common, :domain, :data, :bot). Currently delegates to the
// common convention; it will diverge from :app in later epic tasks (no `application` plugin,
// explicitApi() candidate). Kept as a named ADR-0001 deliverable.
plugins {
    id("spovishun.kotlin-common")
}
