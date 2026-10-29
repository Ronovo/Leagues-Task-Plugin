# Security

## Design

The plugin makes no connection to the tracker and holds no token. Its only network call is
the public Jagex hiscores lookup that Autocomplete Tasks uses for bosses with no in-client
kill count. Syncing to a tracker is done by the separate
[Ronovo's Sync Tool](https://github.com/Ronovo/Ronovos-Sync-Tool) plugin; see its
`SECURITY.md`. The Sync Progress button only posts an in-client RuneLite event asking that
plugin to sync.

## Reporting a vulnerability

Please report suspected security issues privately to the repository owner (open a GitHub
security advisory, or contact via the profile on <https://github.com/Ronovo>) rather than in
a public issue. Include the plugin version and enough detail to reproduce.
