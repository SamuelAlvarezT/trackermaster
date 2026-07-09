# Implementation Plan - App Upgrades & UI Polish

This plan addresses a series of enhancements for Habits, Tasks, Focus, and Journal sections, as well as enabling direct sharing of images/links from external apps.

## User Review Required

> [!WARNING]
> Database schema updates are required for `tasks`, `journal_entries`, and `transactions` tables. Room `fallbackToDestructiveMigration()` is enabled, which will reset database storage in development.

## Proposed Changes

---

### Core Data Models & Database

#### [MODIFY] [Models.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/core/domain/src/main/kotlin/com/trackermaster/core/domain/model/Models.kt)
- Add `SubTask` domain model: `data class SubTask(val title: String, val completed: Boolean = false)`
- Modify `Task` model to add `createdAt: LocalDateTime` and `subtasks: List<SubTask>`.
- Add `moodLevel: Int?` and `attachments: List<Attachment>` to `JournalEntry`.
- Add `Attachment` domain model mapping to `AttachmentEntity`.
- Add `imageUri: String?` to `Transaction` model.

#### [MODIFY] [Entities.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/core/database/src/main/kotlin/com/trackermaster/core/database/entity/Entities.kt)
- Update `TaskEntity` to add `createdAtEpoch: Long` and `subtasksJson: String`.
- Update `JournalEntryEntity` to add `moodLevel: Int?`.
- Update `TransactionEntity` to add `imageUri: String?`.

#### [MODIFY] [Mappers.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/core/data/src/main/kotlin/com/trackermaster/core/data/mapper/Mappers.kt)
- Implement `encodeSubtasks` and `decodeSubtasks` using delimiters.
- Update mapping extension functions for `Task`, `JournalEntry`, `Attachment`, and `Transaction` to match new schema properties.

#### [MODIFY] [TrackermasterDatabase.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/core/database/src/main/kotlin/com/trackermaster/core/database/TrackermasterDatabase.kt)
- Increment database version to `4`.

#### [MODIFY] [Daos.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/core/database/src/main/kotlin/com/trackermaster/core/database/dao/Daos.kt)
- Add `@Query("SELECT * FROM attachments") fun observeAllAttachments(): Flow<List<AttachmentEntity>>` to `JournalDao`.

#### [MODIFY] [Repositories.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/core/data/src/main/kotlin/com/trackermaster/core/data/repository/Repositories.kt)
- Update `JournalRepository.observeEntries()` to combine entries with all observed attachments.
- Update `JournalRepository.save(entry)` to save its attachments via `journalDao.insertAttachment()`.
- Add generic `updateTask` to `TaskRepository`.
- Seed a default account in `ExpenseRepository` or `ExpenseViewModel` if empty to prevent quick expense crashes.

---

### Habits UI Polish

#### [MODIFY] [HabitsScreens.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/feature/habits/src/main/kotlin/com/trackermaster/feature/habits/HabitsScreens.kt)
- Reposition Active/Archive headers to sit tightly near the top margin.
- Render weekday column headers (e.g., Fri, Sat, Sun...) exactly once at the top of the Habits screen, aligned horizontally with the habit logs.
- Remove weekday abbreviation text from inside the individual `HabitRow` cards.
- Reduce card internal padding and day circle sizes to make each habit element smaller and more compact.
- Configure nested Scaffold `contentWindowInsets = WindowInsets(0, 0, 0, 0)` and optimize list paddings to eliminate the large empty bottom space above the main navigation bar.

---

### Tasks & Subtasks Improvements

#### [MODIFY] [TasksScreens.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/app/src/main/java/com/trackermaster/app/tasks/TasksScreens.kt)
- Add UI to display how long a task has been pending (e.g., "Pending for 3 days").
- Add subtask support within each task:
  - Progress bar/indicator of completed subtasks.
  - Expandable detail drawer showing list of subtasks.
  - Interactive checkboxes to toggle subtasks.
  - Small text field to add a new subtask dynamically.

---

### Professional Focus Section

#### [MODIFY] [FocusScreens.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/feature/focus/src/main/kotlin/com/trackermaster/feature/focus/FocusScreens.kt)
- Revamp UI with a sleek, circular progress indicator for the timer.
- Introduce daily statistics (Completed time, weekly focus score).
- Implement a comprehensive history panel that groups past focus sessions by day (e.g. Today, Yesterday), displaying category icon, elapsed time, and status.
- Add Start/Pause/Stop actions that save partial sessions if stopped early.

---

### Enhanced Journal & Formats

#### [MODIFY] [JournalScreens.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/feature/journal/src/main/kotlin/com/trackermaster/feature/journal/JournalScreens.kt)
- Add mood selector (1-5 with emojis) inside the "New Journal Entry" builder.
- Add attachment buttons in the editor: Image, Audio, PDF.
- Implement file picking and copying of picked files to internal app storage for persistence.
- Implement `JournalEntryDetailDialog` to show the full entry, custom mood, and attachments.
- Render media inline: images with `AsyncImage`, audio files with an interactive play/pause player, and PDFs with clickable icons.

---

### Share Intent Receiver

#### [NEW] [ShareIntentHandler.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/app/src/main/java/com/trackermaster/app/ShareIntentHandler.kt)
- Singleton object carrying shared text or image URI state.

#### [MODIFY] [MainActivity.kt](file:///c:/Users/usu/Desktop/habit%20racker%20app/app/src/main/java/com/trackermaster/app/MainActivity.kt)
- Extract shared text/images inside `onCreate` and `onNewIntent` and update `ShareIntentHandler`.
- In `TrackermasterRoot`, observe the shared content and display an import selection dialog ("Log Expense" or "Add to Journal").
- Route user to target section and prepopulate transaction fields or journal attachment.

#### [MODIFY] [AndroidManifest.xml](file:///c:/Users/usu/Desktop/habit%20racker%20app/app/src/main/AndroidManifest.xml)
- Register intent filters for `ACTION_SEND` with mimeTypes `text/plain` and `image/*`.

---

### Module Dependencies

#### [MODIFY] [build.gradle.kts (App/Journal/Expense)](file:///c:/Users/usu/Desktop/habit%20racker%20app/app/build.gradle.kts)
- Add `libs.coil.compose` dependency to support image loading.

## Verification Plan

### Automated Tests
- Build and run the app to ensure compiled files build successfully.

### Manual Verification
- Test Habits screen layout margins and verify weekday headers alignment.
- Verify subtasks can be added, completed, and deleted. Check pending duration display.
- Test Focus timer and verify sessions are stored and listed correctly grouped by day.
- Add a new journal entry with a selected mood level and attach an image, audio, and PDF file. Verify rendering in detailed view.
- Share an external image/text link to Trackermaster, select destination (Money/Journal), and verify correct pre-population.
