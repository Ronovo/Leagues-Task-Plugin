# Security

## Design

The plugin is **write-only** with respect to the tracker. It posts your progress to the
Server URL you configure and reads only the HTTP status code back — the response body is
never parsed or acted on, so a malicious or compromised tracker cannot feed data into the
plugin. HTTP redirects are not followed, so a compromised server cannot bounce your token to
another host.

## The API token

- Sent as `Authorization: Bearer <token>` to `<serverUrl>/api/sync/*`.
- Stored by RuneLite in its own config (`settings.properties`) **unencrypted** — the
  `secret` flag only hides it from the settings UI. Use a personal, revocable token rather
  than a shared or long-lived secret where possible.
- A non-localhost `http://` Server URL is **rejected** — the plugin will not send the token
  in cleartext over the network. Use `https://` for a remote tracker.
- The token is never written to any log line, error message, or UI text.

## What a sync sends

Character name, account hash, quest completion states, achievement diary progress, combat
achievement progress, completed-task list, and — only when captured from the in-game
Collection Log — the collection log. Nothing is sent until you enter a Server URL and token
and press a Sync button. There is no default or shared server.

## Reporting a vulnerability

Please report suspected security issues privately to the repository owner (open a GitHub
security advisory, or contact via the profile on <https://github.com/Ronovo>) rather than in
a public issue. Include the plugin version and enough detail to reproduce.
