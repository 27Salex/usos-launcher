# Leisure and Blocking

- **Sections** by use; Leisure apps are frozen until you ask for time.
- `LeisureMachine` (pure, injected `Clock`) holds the rules; `LeisureRepository` (DataStore) is the source of truth and exposes `status`. The "Thawing" wait lives only in memory. "No budget" = remaining < 15 min.
- `HomeActivity` ticks every 500 ms while STARTED and cancels thawing in `onStop`. `HomeViewModel.openApp` always refuses frozen leisure apps (it opens the Leisure section).
- `BlockerService` (AccessibilityService) observes `LeisureRepository.status` and uses the pure `BlockerRules`. On a blocked app it posts to `AppContainer.pendingAlert` (same process) and performs `GLOBAL_ACTION_HOME`; the ViewModel ignores the accompanying HOME for 2 s. Needs the accessibility service enabled (on Android 13+ maybe "Allow restricted settings").
- Usage (`UsageAggregator`, pure; `UsageRepository`): `queryEvents` RESUMED/PAUSED, sessions cut at local midnight, "this week" from Monday, main profile only.
- `NotificationWatcher` publishes only `package/serial` of non-ongoing notifications, never content.
