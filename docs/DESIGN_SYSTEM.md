# Vibe Engineering — Cosmic Cyber Design System

**Status:** foundation documented; application-wide adoption is in progress. This document distinguishes implemented source values from migration targets. It is not evidence that every screen already follows the system.

## 1. Product visual direction

Vibe Engineering combines three visual references:

- **Cosmic:** deep-space surfaces, restrained radial light, orbital geometry and atmospheric depth.
- **Cybernetic:** cyan/violet signal accents, precise status indicators and luminous focus states.
- **Engineering:** dense but legible information hierarchy, explicit system states, monospaced technical output and predictable controls.

The interface must remain a working engineering tool. Visual effects must not obscure content, reduce contrast, delay interaction or replace real operations with decorative simulations.

## 2. Implemented shared tokens

Source: `app/src/main/java/com/example/ui/theme/VibeDesignTokens.kt`.

| Token | Current value | Intended role |
|---|---:|---|
| SpaceBackground | `#050914` | Main dark canvas |
| SpacePanel | `#E6101B2B` | Translucent panel |
| SpacePanelRaised | `#F0142235` | Raised panel |
| Cyan | `#28D7F2` | Primary signal / focus |
| Violet | `#9B6BFF` | Secondary accent |
| TextPrimary | `#EAF8FF` | Primary text on dark surfaces |
| TextMuted | `#9CB1C8` | Secondary text |
| Grid | `#1A8B949E` | Low-emphasis grid |
| Success | `#36E6A0` | Positive state |
| Error | `#FFFF5875` | Error state |

### Geometry and spacing

| Token | Value |
|---|---:|
| Shape.Compact | 8 dp |
| Shape.Control | 13 dp |
| Shape.Panel | 22 dp |
| Spacing.Micro | 4 dp |
| Spacing.Small | 8 dp |
| Spacing.Medium | 14 dp |
| Spacing.Large | 20 dp |
| Spacing.Section | 28 dp |

Use these tokens instead of introducing new raw values for recurring visual roles. Screen-specific exceptions must have a reason.

## 3. Existing Material theme and migration boundary

The current `Theme.kt` still maps Material 3 to the older Engineering palette from `Color.kt`:

- Dark background `#0D1117`, surface `#161B22`, primary `#58A6FF`.
- Light mode remains a separate GitHub-inspired neutral palette.
- `EngCyan` is currently `#39D353` (green), despite its name.
- The Login screen uses the new Vibe tokens locally; the Material theme has not yet been migrated globally.

Do not change `EngCyan` by name-based assumption. Before migrating any legacy accent, audit its usages and semantic role. Global theme migration must preserve light-theme contrast and existing screen behavior and should be validated on rendered screens.

## 4. Typography

Existing source: `app/src/main/java/com/example/ui/theme/Type.kt`.

- IBM Plex Sans Arabic is the UI family for Arabic and general interface copy.
- JetBrains Mono is reserved for code, logs, identifiers and technical metadata.
- The source currently defines display, headline, title, body and label styles. New screens should use MaterialTheme typography rather than ad-hoc font sizes.
- Keep technical strings visually distinct without allowing long code/IDs to force horizontal overflow.

## 5. Component and interaction rules

- **Panels:** use consistent panel surface, border, radius and internal spacing. Reserve gradients for hierarchy or active state, not every container.
- **Primary actions:** one clear primary action per task region; show loading state and prevent duplicate submissions.
- **Fields:** persistent labels, visible focus, readable error/help text, correct keyboard type/action, and password visibility controls where relevant.
- **Status:** never communicate success/error by color alone; pair state color with text or an icon and accessible description.
- **Feedback:** implement loading, empty, success, error and offline states for data-driven features.
- **Motion:** use short, purposeful transitions for state changes; avoid continuous decorative motion that competes with reading or drains battery.
- **Accessibility:** preserve touch targets, text scaling, semantics/content descriptions, keyboard navigation where applicable and sufficient contrast.

## 6. Layout and localization

- Support RTL and LTR explicitly; do not assume all rows, icons or directional navigation are left-to-right.
- Verify compact-width devices, landscape where relevant, large font scale and IME-visible layouts.
- Avoid fixed heights for text-bearing containers unless content clipping has been ruled out.
- Use spacing tokens consistently and keep related controls grouped.

## 7. Screen completion gate

A screen is not complete merely because it compiles or its backend is connected. Its work record must include:

1. Existing visual/interaction problems observed in source or rendered UI.
2. Concrete UI/UX changes made in the same feature cycle as functional work.
3. Real actions, validation and data paths wired; no fake success states.
4. Loading, empty, success, error and offline handling where applicable.
5. RTL/LTR, compact layout, IME, accessibility and text-scale considerations.
6. Test/build evidence and rendered screenshot/device inspection, or a precise record of what could not be verified.

Source inspection alone is not rendered visual verification. Keep the screen open in the task inventory until the required evidence exists.
