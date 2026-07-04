# Spotify UI Style Guide

This document outlines the design principles, colors, typography, and component patterns used in the MuMaFi (Spotify-style) UI. The goal is to provide a reference for other implementations (e.g., Android) to ensure visual consistency.

## Core Principles

- **Dark Mode Only**: The interface is designed exclusively for a dark environment to reduce eye strain and match the aesthetic of modern music players.
- **Content First**: Visual elements (like cover art) are prioritized, with text providing secondary context.
- **Responsive & Accessible**: The UI adapts to different screen sizes and includes improved focus styles for TV remote navigation.

## Color Palette

The UI uses a limited but high-contrast color palette.

### Brand Colors
- **Brand Green**: `#1db954` (Primary brand color for "Songs" mode)
- **Brand Blue**: `#2d5afc` (Primary brand color for "Sets" mode)
- **Active Selection**: `var(--color-brand)` - dynamically switches between Green and Blue based on the current view mode.

### Background Colors
- **Main Background**: `#191414` (Body background - `bg-mumafi-black`)
- **Section Background**: `#121212` (Main content area - `bg-mumafi-dark`)
- **Panel/Card Background**: `#181818` (Sidebar, Track Cards, Filter Bar)
- **Elevated/Hover Background**: `#282828` (Inputs, Hover states - `bg-mumafi-light`)
- **Lighter Hover**: `#3e3e3e` (Active/Secondary hover states)

### Text Colors
- **Primary Text**: `#ffffff` (Titles, active menu items, important labels)
- **Secondary Text**: `#b3b3b3` (Artists, durations, inactive menu items - `text-mumafi-text`)
- **Accent Text**: Matches the Brand Color (used for active states or highlights)

## Typography

The UI relies on system font stacks for performance and familiarity.

- **Font Family**: `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif`
- **Base Sizes**:
  - **11px**: Uppercase labels with `tracking-wider` (e.g., "GENRE", "JAAR").
  - **12px**: Library headers, secondary metadata.
  - **13px**: Track titles (Player), Menu items (Sidebar).
  - **14px**: Standard body text, card titles.
  - **20px+**: View titles, greetings.
- **Weights**:
  - **Bold (700)**: Used for titles, active navigation, and buttons.
  - **Medium (500)**: Used for secondary navigation and list items.
  - **Normal (400)**: Used for general metadata.

## Layout

### Desktop Layout
- **Sidebar**: Fixed on the left (`240px` to `300px`).
- **Main Content**: Scrollable area in the center/right.
- **Player**: Fixed at the bottom, full width.

### Mobile Layout
- **Top Bar**: Minimal header with branding (left) and Settings/Profile (right). Should hide on scroll down and reappear on scroll up.
- **Main Content**: Single column layout. Track lists should use simplified rows (Title/Artist only, no duration or album columns to save space).
- **Floating Player (Mini Player)**: 
    - Compact bar (`64px` height) fixed above the navigation.
    - Displays: Small cover art, Track Title, Artist, and Play/Pause + Next buttons.
    - Progress: A thin (2px) progress line at the very top of the bar.
- **Bottom Navigation**: Tab bar for quick access (Home, Search, Library). Fixed at the bottom with `env(safe-area-inset-bottom)`.

### Full-Screen Player (Proposed)
Tapping the Mini Player should expand into a full-screen view:
- **Header**: Dismiss button (chevron down) and current Playlist/Album title.
- **Cover Art**: Large, centered, aspect-square.
- **Controls**: Large Play/Pause button flanked by Skip Back/Next.
- **Progress**: Prominent seek bar with time stamps.
- **Secondary Actions**: Shuffle, Repeat, Heart (Like), and Queue access at the bottom.

## Mobile Experience & Patterns

### Touch Targets
- All interactive elements (buttons, list items) should have a minimum touch target of `44x44px` to ensure ease of use.
- Increase vertical padding in track lists for easier selection.

### Gestures
- **Swipe on Player**: Swipe left/right on the Mini Player or Full-Screen cover art to skip tracks.
- **Pull-to-Refresh**: Standard gesture for refreshing library or playlists.
- **Edge Swipes**: Support standard OS gestures for "Back" navigation.

### Bottom Sheets
Instead of traditional dropdowns or modals, use "Bottom Sheets" for:
- Track options (Add to playlist, View Artist, Go to Album).
- Sorting and Filtering options.
- Device selection (Connect).

### Typography Adjustments
- Increase track title font size to `15px` or `16px` for better readability.
- Ensure secondary metadata (Artist) remains legible at `13px`.

### Dynamic Branding
- Bottom Navigation active state and Mini Player progress bar must adapt to the `viewMode` (`Brand Green` for Songs, `Brand Blue` for Sets).
- The "Now Playing" background or accents in the Full-Screen player should reflect the primary color of the current track's album art (optional but recommended for a premium feel) or stick to the `viewMode` brand color.

## Components

### Track Cards
- **Container**: `bg-[#181818]`, `p-4`, `rounded-lg`.
- **Hover State**: `bg-[#282828]`.
- **Cover Art**: `aspect-square`, `shadow-[0_8px_24px_rgba(0,0,0,0.5)]`, `rounded`.
- **Play Button**: Circular green/blue button that appears on hover.

### Track List (Table)
- **Row**: `px-4 py-2`, `rounded`, `grid` layout.
- **Hover State**: `bg-white/10`.
- **Alternating Rows**: Not used; separation is handled by hover states and spacing.

### Filter Bar
- **Container**: `bg-[#181818]`, `p-4`, `rounded-lg`, `mb-6`.
- **Search Input**: Pill-shaped (`rounded-[20px]`), `bg-[#282828]`, `px-4 py-2`.
- **Selects/Dropdowns**: Minimal styling, `bg-[#282828]`, `rounded`.

### Buttons & Inputs
- **Primary Buttons**: Circular, Brand Color background, Black icon/text.
- **Secondary Buttons**: Transparent background, White/Gray text, scales up slightly on hover (`hover:scale-105`).
- **Focus Rings**: `3px solid var(--color-brand)` with `outline-offset: 2px` and `box-shadow` for accessibility.

### Search Results (Grouped)
- **Overlay**: Appears as a floating panel below the search bar (`bg-[#282828]`, `rounded-md`, `shadow-2xl`).
- **Sections**: Group results by category: Tracks, Artists, Albums, and Genres.
- **Section Headers**: `text-white`, `font-bold`, `text-sm`, with a "Zie meer" button on the right for expansion.

### Scrollbars
- **Desktop**: Custom thin scrollbars (`8px` width) with `#4e4e4e` thumb color and transparent track. Hover thumb color: `#5e5e5e`.
- **Mobile**: Use standard OS scrollbars (hidden where possible using `scrollbar-hide` for a cleaner look).

## Assets
- **Fallback Cover**: `/images/spotify-fallback.svg`
- **Unknown Artist**: `/images/unknown-artist.svg`
- **Icons**: Standard SVG paths (often 16x16 or 24x24).

## View Modes
The UI supports two main "flavors":
1. **Songs Mode**: The classic Spotify green aesthetic.
2. **Sets Mode**: A blue-themed alternative for DJ sets or long-form content.
Implementations should listen to the `viewMode` state and update `--color-brand` accordingly.

## Animations & Transitions

- **Page Transitions**: Use simple "Fade" transitions (`200ms`) when switching between main views (Home, Artist, Album).
- **Modals**: Appear with a "Fade + Scale" effect (`200ms`, starting at `95%` scale).
- **Hover Effects**:
    - Cards and list items should have a subtle background color change (`bg-[#282828]` for cards, `bg-white/10` for list rows).
    - Buttons should scale slightly (`1.05x`) on hover to indicate interactivity.
- **Loading State**: Use "Pulse" animations (`animate-pulse`) for skeleton loaders or text messages during long operations (like library scans).

## States & Error Handling

- **Empty States**: Display a centered icon (e.g., Search or Music icon) in `Secondary Text` color, with a clear title and a call-to-action button (e.g., "Ontdek muziek" or "Reset filters").
- **Error States**: Show a non-intrusive toast or a dedicated error section with a "Retry" button. Use a muted red for critical errors if necessary, but prefer staying within the dark theme palette.
- **Skeleton Loaders**: While tracks are fetching, show 5-10 placeholder rows with gray boxes for cover art and title.

## API & Data Integration (for Developers)

To maintain consistency with the Web UI, other implementations should follow these data patterns.

### Authentication
Use standard Subsonic API parameters: `u={user}&p={pass}&v=1.12.0&c=mumafi&f=json`.

### Extended Endpoints
The UI relies on custom/extended endpoints for a better experience:
- **`getSpotifyTracks`**: 
    - `sort`: `recent`, `added`, `mostplayed`, `recentlyplayed`, `rating`, `alpha`, `random`.
    - `deduplicate=true`: Aggregates duplicate tracks across different releases.
    - `minRating`: Supports filtering by user-specific ratings.
    - `maxDuration`: Filtering by maximum duration (in **minutes**).
    - `query`: For real-time filtering within the current view.
- **`getSpotifyHistory`**: 
    - `offset`, `count`: For paginated listen history.
    - Returns grouped entries with `listenedAt` timestamps.

### Deduplication Logic (MuMaFi Algorithm)
To ensure a clean library, implementations should deduplicate tracks using the following rules:
1. **Normalization**: Strip metadata in parentheses from titles (e.g., "(Radio Edit)") and normalize artist names (sort multiple artists alphabetically).
2. **Ranking Score**:
    - **Quality**: `FLAC` (10000) > `MP3 320` (5000) > `AAC/M4A` (3000) > `MP3 192` (2000).
    - **Version**: `Radio Edit` (+500) > `Original Mix` (+300) > `Standard` (+200) > `Extended Mix` (+100).
3. **Selection**: If multiple versions of the same artist + title exist, only display the one with the highest total score.

### Sorting Logic
| ID | Label | Backend Method |
|----|-------|----------------|
| `recent` | Recent uitgebracht | `OriginalDateDescAndRelease` |
| `added` | Recent toegevoegd | `DateDescAndRelease` |
| `mostplayed` | Meest beluisterd | `MostPlayed` |
| `rating` | Op rating | `Rating` |
| `alpha` | Alfabetisch | `Name` |

### Image Fallbacks
- If `coverArt` is missing or fails to load, use `/images/spotify-fallback.svg`.
- For artists without images, use `/images/unknown-artist.svg`.
