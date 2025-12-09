
## 👨‍🦳 App Design Plan: "Pee Reminder"

The core function is a **simple, reliable timer** that triggers an alarm/notification at set intervals.

### 1. ⚙️ Core Functionality

| Feature | Description |
| :--- | :--- |
| **Interval Timer** | Allows the user (or you) to set the desired frequency (e.g., 2 hours). |
| **Persistent Service** | Ensures the timer runs reliably in the background, even if the app is closed. |
| **Notification/Alarm** | A clear, unmissable alert when the interval is up. |
| **Start/Stop Toggle** | A prominent, easy-to-use button to activate and deactivate the reminder. |

---

### 2. 📱 UI/UX Design for the Elderly

The goal is **minimalism** and **high contrast**.

#### **A. Key Visuals and Interaction**

* **Font Size:** Use a **large, readable, sans-serif font** (like Roboto or Noto Sans) for all text. The text size should be at least 18-20sp.
* **Color Scheme (High Contrast):**
    * **Background:** Dark gray or black (easier on the eyes, saves battery).
    * **Text/Icons:** Bright white or a light, highly saturated color (e.g., bright yellow or lime green).
    * **Action Button (Start/Stop):** Use high-contrast colors (e.g., bright **RED** for STOP and bright **GREEN** for START).
* **Touch Targets:** All buttons must be **large** (minimum 48dp x 48dp, ideally larger for elderly users) and have ample space around them.
* **Avoid:** Complex menus, swiping gestures, small icons, and animated distractions.

#### **B. App Screens**

##### **i. Main Screen: The Timer & Control**

This is the primary screen your dad will see. It should show the current status immediately.

* **Top (Status):** A large, centered display showing the current status.
    * **Reminder ON:** Display **"ACTIVE"** in large **GREEN** text.
    * **Reminder OFF:** Display **"PAUSED"** in large **RED** text.
* **Middle (Next Reminder):** Large, simple text: **"Next Reminder At: [Time]"** or **"Time Remaining: [X] minutes"**.
* **Bottom (Control):** A single, massive, clearly labeled toggle button.
    * **Button State 1 (Active):** Button reads **"PAUSE REMINDER"** and is **RED**.
    * **Button State 2 (Paused):** Button reads **"START REMINDER"** and is **GREEN**.

##### **ii. Settings Screen (Accessed by you/caretaker)**

This screen is for setting the frequency and preferences, likely accessed via a small settings icon (⚙️) in the corner.

* **Interval Setter:** Use a **large, clear number picker** or drop-down menu with options like: **1 hour, 1.5 hours, 2 hours, 3 hours.**
* **Quiet Hours:** A simple toggle to **"Disable reminders overnight"** (e.g., between 10 PM and 7 AM).
* **Sound/Vibration:** Large buttons to choose **"Sound"**, **"Vibration"**, or **"Both"** for the alert.

---

### 3. 🔔 The Reminder Alert (Notification/Alarm)

This is the most critical part of the UX: getting his attention effectively.

* **Alert Type:** Use a **full-screen alarm activity** that pops up over whatever he is doing, not just a small notification bar icon. This ensures it's seen.
* **Visual:** A simple, friendly image (e.g., ) in the center.
* **Message:** Huge, simple text: **"Time to Go!"** or **"Pee Reminder!"**
* **Audio:** A **pleasant, loud, non-jarring sound** (avoiding harsh beeps or sirens) that repeats until acknowledged.
* **Dismiss Button:** A single, massive, central button labeled **"I HEARD IT"** or **"OK"** to stop the alarm and restart the timer for the next interval.
