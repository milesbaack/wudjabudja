# WudjaBudja UI Design Language — "Compass"

**Version:** 1.0  
**Applies to:** WudjaBudja Desktop Frontend (JavaFX 21 + FXML + CSS)  
**Primary Stylesheet:** `Code/frontend/src/main/resources/com/wudjabudja/styles/theme.css`  
**Icon & Theme Utilities:** `com.wudjabudja.ui.Icons`, `com.wudjabudja.ui.UiTheme`

---

## 1. Design Vision & Principles

WudjaBudja eliminates the stress of group travel planning by turning messy group preferences into consensus itineraries. The interface should feel like a **calm, modern travel concierge** — never like a dense spreadsheet or legacy desktop utility.

### Core Principles
1. **Clarity Over Clutter:** Generous whitespace, clear visual hierarchy, and scannable cards replace dense walls of form controls and repetitive borders.
2. **Guided Journey:** Group trip planning follows a natural arc — **Plan → Group & Quiz → Itinerary → Vote → Briefing**. Every screen makes the current trip context obvious and highlights the next logical action.
3. **Tactile & Responsive:** Every interactive element (buttons, cards, pills, inputs, sliders, nav links) provides clear hover, focus, active, and disabled feedback. Whole option cards are clickable, not just tiny radio buttons or checkboxes.
4. **One Design System, Zero Ad-Hoc Styling:** All colors, radii, elevation, typography, and native control overrides live in a single semantic stylesheet (`theme.css`) rather than scattered inline `-fx-style` strings.
5. **High-Contrast Legibility:** All body text, badges, and buttons meet WCAG AA contrast ratios against their surfaces.

---

## 2. Color System

Compass pairs a **warm alabaster canvas** and **crisp white elevated cards** with a **deep midnight-obsidian navigation rail**, anchored by a signature **Coastal Teal** primary brand color and **Sunset Amber** for consensus winners.

### 2.1 Surfaces & Neutrals

| Token | Hex | Usage |
| :--- | :--- | :--- |
| `bg-canvas` | `#F4F6F9` | Main workspace background behind cards |
| `bg-surface` | `#FFFFFF` | Primary cards, modals, forms, elevated containers |
| `bg-subtle` | `#F8FAFC` | Recessed wells, KPI strips, nested info cards, inactive option cards |
| `bg-muted` | `#EEF2F6` | Subtle hover fills, inactive pills, progress bar tracks |
| `bg-sidebar` | `#0B1220` | Persistent left navigation rail & dark hero surfaces |
| `bg-sidebar-card` | `#131D31` | Active trip card & user profile footer in sidebar |
| `bg-sidebar-hover` | `#1A2740` | Sidebar nav hover background |
| `border-hairline` | `#E5E9F0` | Default card & container borders |
| `border-input` | `#CFD7E3` | Text inputs, dropdowns, secondary button borders |
| `border-strong` | `#94A3B8` | Input hover borders, subtle dividers |

### 2.2 Text Hierarchy

| Token | Hex | Usage |
| :--- | :--- | :--- |
| `text-primary` | `#0F172A` | Page titles, headings, primary values, strong labels |
| `text-secondary` | `#475569` | Body copy, descriptions, secondary metadata |
| `text-muted` | `#64748B` | Supporting captions, subtitles, helper text |
| `text-subtle` | `#94A3B8` | Placeholders, timestamps, overlines, sidebar inactive items |
| `text-on-dark` | `#F8FAFC` | Primary text on dark obsidian / hero / primary button surfaces |
| `text-on-dark-muted` | `#94A3B8` | Secondary text on dark obsidian surfaces |

### 2.3 Brand & Semantic Accents

| Role | Primary | Hover / Strong | Soft Tint | Border Tint | Text on Tint |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Brand (Coastal Teal)** | `#0D9488` | `#0F766E` | `#F0FDFA` | `#99F6E4` | `#0F766E` |
| **Action Dark (Obsidian)** | `#0F172A` | `#1E293B` | `#F1F5F9` | `#CBD5E1` | `#0F172A` |
| **Success (Emerald)** | `#059669` | `#047857` | `#ECFDF5` | `#A7F3D0` | `#065F46` |
| **Winner / Gold (Amber)** | `#D97706` | `#B45309` | `#FFFBEB` | `#FCD34D` | `#92400E` |
| **Warning (Warm Orange)** | `#EA580C` | `#C2410C` | `#FFF7ED` | `#FED7AA` | `#9A3412` |
| **Danger (Rose)** | `#DC2626` | `#B91C1C` | `#FEF2F2` | `#FECACA` | `#991B1B` |
| **Subgroup / AI (Violet)** | `#7C3AED` | `#6D28D9` | `#F5F3FF` | `#DDD6FE` | `#5B21B6` |
| **Info / Busy (Sky)** | `#0284C7` | `#0369A1` | `#F0F9FF` | `#BAE6FD` | `#075985` |

### 2.4 Activity Category Chips

| Category | Background | Text |
| :--- | :--- | :--- |
| `food` | `#FEF3C7` | `#92400E` |
| `outdoors` | `#DCFCE7` | `#166534` |
| `culture` | `#EDE9FE` | `#5B21B6` |
| `nightlife` | `#E0E7FF` | `#3730A3` |
| `sports` | `#FEE2E2` | `#991B1B` |
| `shopping` | `#FCE7F3` | `#9D174D` |
| `relaxation` | `#E0F2FE` | `#075985` |
| `tour` | `#F1F5F9` | `#334155` |

---

## 3. Typography

### Font Stack
JavaFX CSS does not evaluate comma-separated web font fallbacks and macOS `System` triggers glyph kerning/clipping bugs in OpenJFX 21. Compass therefore specifies a single clean installed geometric sans-serif family per platform via `theme.css` and `UiTheme`:
- **Primary UI:** `"Avenir"` on macOS (resolved dynamically with fallback to `"Helvetica"` → `"Segoe UI"` on Windows → `"Arial"` / `"SansSerif"`)
- **Monospace (Join Codes, Timers):** `"Menlo"` (with `"Monospaced"` fallback)

### Type Scale

| Style Class | Size | Weight | Color | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `.hero-title` | `26px` | Bold (700) | `#FFFFFF` / `#0F172A` | Hero destination title, auth headline |
| `.page-title` | `22px` | Bold (700) | `#0F172A` | Top-level view headings |
| `.section-title` | `17px` | Bold (700) | `#0F172A` | Major section & day headers |
| `.card-title` | `15px` | Bold (700) | `#0F172A` | Card titles & time block labels |
| `.item-title` | `14px` | Bold (700) | `#0F172A` | Activity option titles, traveler names, trip rows |
| `.body-text` | `13px` | Regular (400) | `#475569` | Descriptions, instructions, summaries |
| `.meta-text` | `12px` | Regular/Medium | `#64748B` | Dates, neighborhoods, helper notes |
| `.field-label` | `12.5px` | Bold (700) | `#334155` | Form input labels |
| `.eyebrow` | `10.5px` | Bold (700) | Accent | Uppercase section pill tags (e.g., `ACTIVE TRIP`, `DAY 1`) |
| `.stat-value` | `18px–32px` | Bold (700) | `#0F172A` / `#0D9488` | KPI numbers, budget totals, radius display |

---

## 4. Layout, Grid & Spacing

### Window Dimensions
- **Authentication Shell (`LoginView`, `RegisterView`):** `960 × 640` (min `820 × 580`), split-panel layout with brand showcase on the left and clean form card on the right.
- **Main Application Shell (`MainView`):** `1240 × 800` (min `980 × 660`), `BorderPane` with a fixed `256px` dark left sidebar and scrollable, max-width-constrained workspace on the right.

### Spacing Scale (4px Base Unit)
- `4px` — Tight inline icon/label gaps, badge padding
- `8px` — Form label-to-input spacing, chip gaps, compact rows
- `12px` — Button bar spacing, list row gaps, option card gaps
- `16px` — Internal card element spacing, grid gaps
- `20px` — Vertical spacing between cards and sections
- `24px` — Standard card internal padding (`20px 24px` or `24px`)
- `32px–36px` — Workspace viewport margins (`28px 36px 40px 36px`)

### Content Width Restraint
Inside every `ScrollPane`, center the primary `VBox` content container and cap wide forms (`maxWidth="980"` for wizards/setup, `maxWidth="1160"` for dashboards, itineraries, voting, and briefings) so content stays crisp and readable on large desktop displays.

---

## 5. Surfaces, Radii & Elevation

| Component | Radius | Border | Shadow |
| :--- | :--- | :--- | :--- |
| Primary Card (`.card`) | `14px` | `1px solid #E5E9F0` | `dropshadow(gaussian, rgba(15,23,42,0.05), 14, 0, 0, 4)` |
| Hero Banner (`.hero-card`) | `16px` | `1px solid rgba(255,255,255,0.08)` | `dropshadow(gaussian, rgba(11,18,32,0.18), 24, 0, 0, 8)` |
| Recessed Well (`.well`) | `10px` | `1px solid #E5E9F0` | None |
| Selectable Card (`.choice-card`) | `12px` | `1.5px solid #E5E9F0` → `#0D9488` when selected | Subtle teal glow when selected |
| Winner Card (`.option-card-winner`) | `12px` | `2px solid #F59E0B` | `dropshadow(gaussian, rgba(245,158,11,0.14), 14, 0, 0, 3)` |
| Inputs & Dropdowns | `10px` | `1px solid #CFD7E3` → `#0D9488` on focus | Focus ring `dropshadow(gaussian, rgba(13,148,136,0.22), 8, 0, 0, 0)` |
| Buttons | `10px` (compact `8px`) | Variant-specific | Subtle depth on primary CTAs |
| Badges & Chips | `999px` (pill) | None or `1px` tint | None |

---

## 6. Component Specifications

### 6.1 Persistent Sidebar (`MainView`)
- **Width:** `256px` fixed, background `#0B1220`.
- **Brand Lockup:** Stylized teal compass emblem (`SVGPath`) + `WudjaBudja` bold wordmark + `Group Travel Planner` subtitle.
- **Active Trip Context Pill:** Dark elevated card (`#131D31`, subtle `#1E293B` border) displaying the currently open trip's destination/title and dates (or a subtle `"No active trip selected"` prompt) so users always know which trip they are editing.
- **Grouped Navigation:**
  - `WORKSPACE`: **Plan a Trip**, **Dashboard**
  - `ACTIVE TRIP`: **Shared Trip**, **Preferences Quiz**, **Itinerary**, **Group Voting**, **Trip Briefing**
  - Each nav button pairs a `16px` crisp vector icon with text. Active item uses a translucent teal pill (`rgba(13, 148, 136, 0.18)`), bright `#2DD4BF` icon, crisp white bold text, and a left accent bar (`3px` teal).
  - Trip-scoped links subtly dim when no trip is active (still clickable to display the helpful redirect flash message).
- **Bottom Profile Card:** Initials avatar badge (teal gradient circle), user name, email, and a clean ghost **Log Out** button with icon.

### 6.2 Button Hierarchy

| Class | Appearance | When to Use |
| :--- | :--- | :--- |
| `.btn-primary` | Solid Coastal Teal (`#0D9488`), white bold text | Primary CTA per view (Create Trip, Submit Votes, Generate, Next, Save & Continue) |
| `.btn-accent` | Solid Obsidian (`#0F172A`), white bold text | Secondary high-emphasis CTA (e.g., Sign In, Open Voting from Dashboard) |
| `.btn-soft` | Teal tint (`#F0FDFA`), teal border & text | Quick contextual actions (Edit Quiz, Open Trip, Active Presets) |
| `.btn-secondary` | Crisp white, slate border (`#CFD7E3`), dark text | Secondary actions (Refresh, Back, Reset Filters, Secondary Nav) |
| `.btn-ghost` | Transparent, teal or slate text, subtle hover tint | Inline links, tertiary controls |
| `.btn-danger-outline` | White, soft rose border, rose text, rose hover fill | Destructive actions (Remove Traveler) |

### 6.3 Form Controls (Modena Overrides)
All JavaFX Modena chrome is completely replaced in `theme.css`:
- **TextField / PasswordField:** Flat white fill, `10px` corners, `10px 14px` padding, crisp teal focus border + soft glow.
- **ComboBox & DatePicker:** Matching height (`40px`), flat white fill, subtle chevron button, clean floating popup list with `10px` radius and soft drop shadow.
- **Slider:** Thin `6px` rounded track (`#E2E8F0`), `18px` circular teal thumb with `2.5px` white border and subtle shadow.
- **ProgressBar:** `8px` pill track (`#E2E8F0`) with smooth teal gradient bar (`#0D9488 → #14B8A6`).
- **CheckBox & RadioButton:** Clean modern rounded `18px` box/circle with teal fill when selected.
- **ScrollPane / ScrollBar:** Seamless background, thin `8px` rounded translucent scrollbar thumbs, hidden arrow buttons.

### 6.4 Cards & Data Display
- **Hero Banner (Dashboard):** Deep obsidian gradient card (`#0B1220 → #132238`) with teal/emerald accent badges, destination title, dates, traveler roster summary, frosted KPI tiles (Budget, Trip Type, Itinerary Status), and an embedded readiness progress bar.
- **Interactive Quiz Cards (`PreferenceView`):**
  - Clickable 6-step pill stepper in the header so users see all steps (`Schedule`, `Radius`, `Comfort`, `Budget`, `Interests`, `Review`) and can jump directly to any visited/valid step.
  - Step 1 wake-time presets dynamically highlight whichever preset matches the current wake time.
  - Step 3 comfort level cards and Step 5 interest cards are **entirely clickable surfaces** that toggle selection and visibly light up with a teal border and `#F0FDFA` tint.
- **Activity Option Cards (`ItineraryView`, `TripsView`, `VotingView`):**
  - Consistent internal rhythm: Top row (`OPTION N` overline + optional `GROUP WINNER` amber badge) → Bold Title → Time & Neighborhood metadata with subtle vector icons → Category & Cost pill chips → Description → Divider + italicized *Why it fits* note.
  - In `VotingView`, a bottom segmented 3-rank selector (`1st · 3pt`, `2nd · 2pt`, `3rd · 1pt`) with color-coded active states: 1st = Gold/Teal primary, 2nd = Sky/Indigo, 3rd = Slate, with instant visual card highlight when ranked.
- **Status Banners & Loading States:**
  - Busy states use `.banner-busy` (sky tint, compact `18px` spinner, clear status text + live elapsed timer for AI calls).
  - Inline feedback uses `.banner-success` (emerald tint) or `.banner-error` (rose tint) with clear padding and `10px` radius — never raw floating unpadded red text.

### 6.5 Icons & Motion
- **Vector Icons (`Icons.java`):** Stroke-based `16px`–`20px` `SVGPath` icons rendered cleanly at any DPI/Retina display scale (no blurry bitmaps, no inconsistent OS emojis).
- **View Transitions:** `MainController` applies a fast, subtle `160ms` opacity fade-in (`0.0 → 1.0`, `Interpolator.EASE_OUT`) whenever swapping center views, eliminating jarring frame snaps.

---

## 7. Screen-by-Screen Blueprint

1. **Login & Register (`LoginView`, `RegisterView`):** Split-pane desktop layout (`960 × 640`). Left dark obsidian-teal brand panel showcases WudjaBudja's 3 pillars (Group Preferences, AI Subgroup Itineraries, Borda Count Consensus); right panel centers a clean elevated form card with clear validation banners.
2. **Plan a Trip (`TripSetupView`):** Active-trip resume banner (when a trip is open), sleek 2-column form card (Destination + Trip Type; Start/End Dates with live day-count pill + Total Budget), followed by an elevated **Your Trips** switcher list where each trip shows destination, dates, type badge, budget, and a direct **Open Trip** button.
3. **Dashboard (`DashboardView`):** Dark signature **Active Trip Hero Card** with KPI strip and readiness progress bar; balanced 2-column card row (**Your Travel Profile** + **Group Voting & Consensus Highlights**); sleek bottom **Pre-Departure Briefing** callout card.
4. **Shared Trip (`TripsView`):** Compact trip header with action bar; **Travelers Roster Card** with inline add-by-email bar, initials avatars, role/quiz status pills, and clean action buttons (`Take/Edit Quiz`, `Vote`, `Remove`); **Group Activity Options** grouped cleanly by day and time block.
5. **Preferences Quiz (`PreferenceView`):** Header with traveler switcher + interactive 6-step visual stepper + progress bar; clean focused step card with interactive clickable option surfaces and live budget summary; persistent bottom Back/Next bar.
6. **Itinerary (`ItineraryView`):** Header with primary AI Generate/Regenerate action; amber **Consensus "Perfect Days"** budget roll-up banner (when winners exist); segmented view toggle (`All 3 Options` / `Winning Consensus Only`) + clean two-row/wrapped filter & sort toolbar; spacious Day cards with 3-column option cards.
7. **Group Voting (`VotingView`):** Header with Borda scoring legend (`1st = 3pt · 2nd = 2pt · 3rd = 1pt`), clean two-tier voter selector & ballot action bar (`Auto-Fill`, `Clear`, `Submit Votes`); amber **Winning Group Itinerary** summary card; Day & Time Block voting cards with interactive 1st/2nd/3rd rank selectors and per-block completion pills.
8. **Trip Briefing (`BriefingView`):** Header with Generate/Regenerate action; sky-accented **Season, Weather & Crowd Advisory** banner; 2-column **Cultural Customs** & **Language & Local Phrases** cards; interactive **Group Packing Checklist** with live packed counter (`X / Y packed`) and strikethrough/dimmed checked styling; 2-column **Personalized Souvenir Ideas** & **Destination Facts** cards.
