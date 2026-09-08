---
title: AI providers
description: Point ? at Hermes, xAI, OpenAI, or Anthropic. Keys never leave the device except as Bearer tokens.
---

![Settings showing Hermes selected with a LAN base URL](../../../assets/screenshots/settings.png)

Type `settings` → **AI provider**.

## Hermes

Base URL of your instance (OpenAI-compatible `/v1/chat/completions`). Example: `http://192.168.1.10:8642`. API key optional if the instance does not require one. Cleartext LAN URLs are allowed so a home box works.

## xAI

Sign in with SuperGrok / X Premium+ (device-code OAuth at `auth.x.ai`) or paste an API key.

1. Tap **Sign in with SuperGrok**.
2. The browser opens. Enter the code shown in the launcher.
3. The launcher waits for approval (DNS blips during the wait are retried; you do not start over).
4. **Sign out** from settings when you are done.

Default model `grok-4.6`. Hits `https://api.x.ai/v1`.

## OpenAI

Paste an API key. Default model `gpt-4o`. Hits `https://api.openai.com/v1`.

## Anthropic

Paste an API key. Default model `claude-sonnet-4-5`. Hits `https://api.anthropic.com`.

## Tokens

OAuth tokens (xAI) are stored in encrypted prefs on the device and refreshed automatically. An API key remains as a fallback if OAuth is unavailable for your plan.

Switching provider clears the stored OAuth session for the previous one.

See [Privacy](privacy/).
