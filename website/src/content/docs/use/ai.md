---
title: Ask AI
description: Inline LLM answers with ? after you configure a provider.
---

Prefix `?`. Example: `?weather tomorrow`.

Nothing is sent anywhere until you type `?`. There is no analytics.

1. Configure a provider in [AI providers](../configure/ai-providers/).
2. Type `?` plus the question on home.
3. The answer streams inline. A lone `?` is help, not a question.

| Provider | Default model | Endpoint |
| --- | --- | --- |
| Hermes | `default` (whatever your instance serves) | Your base URL, OpenAI-compatible `/v1/chat/completions` |
| xAI | `grok-4.6` | `https://api.x.ai/v1` |
| OpenAI | `gpt-4o` | `https://api.openai.com/v1` |
| Anthropic | `claude-sonnet-4-5` | `https://api.anthropic.com` |

Override the model in settings. Cleartext LAN URLs are allowed so a home Hermes box works.
