This is the technical blueprint. To ensure the app is **reliable** (doesn't get killed by the phone to save battery) and **visible** (wakes up the screen), we need to use specific Android components.

Here is the **Technical Architecture Design** for the "Pee Reminder" app.

### 1\. 🏗️ High-Level Architecture Diagram

To visualize how the components interact, especially when the phone is sleeping:

**The Logic Flow:**

1.  **Main Activity** sets the preference.
2.  **AlarmManager** counts down in the background (System Level).
3.  When time is up, **AlarmManager** wakes the phone and calls the **BroadcastReceiver**.
4.  **BroadcastReceiver** launches the **Full-Screen Activity** (The Alarm Screen).

-----

### 2\. 🧩 Core Components Breakdown

Here are the specific Android classes you need to build and their responsibilities.

#### A. Data Layer (`SharedPrefsManager`)

We don't need a complex database. A simple wrapper around `SharedPreferences` is sufficient.

  * **Purpose:** Persist the state so the app "remembers" even if the phone reboots.
  * **Data Points to Store:**
      * `interval_minutes` (Int)
      * `next_alarm_timestamp` (Long)
      * `is_active` (Boolean)
      * `quiet_hours_start` / `quiet_hours_end` (String or Int)

#### B. The Scheduler (`AlarmScheduler` Class)

This is the most critical technical piece. It interfaces with the Android System `AlarmManager`.

  * **Critical Method:** You must use `setExactAndAllowWhileIdle()`.
      * *Why?* Standard alarms are delayed by Android's "Doze Mode" to save battery. Since this is a health reminder, it needs to be exact.
  * **Logic:**
      * `scheduleAlarm(timeInMillis)`: Sets the system alarm.
      * `cancelAlarm()`: Cancels the pending intent.

#### C. The Listener (`AlarmReceiver` extends `BroadcastReceiver`)

This acts as the bridge between the system clock and your app.

  * **Trigger:** It receives the signal from `AlarmManager`.
  * **Action:**
    1.  Check if current time is inside "Quiet Hours".
    2.  If **YES**: Silently reschedule the next alarm (do not ring).
    3.  If **NO**: Create an `Intent` to launch the `ReminderActivity`.

#### D. The "Face" (`ReminderActivity`)

This is the alarm screen that pops up.

  * **Configuration:** Must be configured in `AndroidManifest.xml` to show over the lock screen.
  * **Key Flags (for Android 10+):**
      * `setShowWhenLocked(true)`
      * `setTurnScreenOn(true)`
  * **User Action:** When the user clicks the massive "OK" button, this activity calls the `AlarmScheduler` to set the *next* alarm before closing itself.

-----

### 3\. ⚠️ Critical Android Permissions & Manifest Settings

To ensure the app works on modern Android versions (12, 13, 14+), you must declare these permissions in your `AndroidManifest.xml`.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" ...>

    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-permission android:name="android.permission.VIBRATE" />

    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />

    <application ...>
        <activity
            android:name=".ReminderActivity"
            android:theme="@style/Theme.AppCompat.NoActionBar"
            android:showOnLockScreen="true"
            android:launchMode="singleTop"
            android:excludeFromRecents="true">
        </activity>

        <receiver android:name=".AlarmReceiver" />
    </application>

</manifest>
```

-----

### 4\. 🔄 The Logical Flow (Step-by-Step)

Here is exactly what happens in the code when your dad uses the app:

1.  **Dad opens app:** Main UI reads `SharedPreferences`. If `is_active == false`, show "START" button.
2.  **Dad presses START:**
      * App calculates: `targetTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000)` (for 2 hours).
      * App calls `AlarmScheduler.setExactAndAllowWhileIdle(targetTime)`.
      * UI updates to Green "ACTIVE".
3.  **App goes to background:** Dad locks the phone.
4.  **2 Hours Later:**
      * `AlarmManager` fires.
      * `AlarmReceiver` wakes up.
      * `AlarmReceiver` launches `ReminderActivity` using a `PendingIntent` with `FLAG_ACTIVITY_NEW_TASK`.
5.  **Alarm Rings:** Screen turns on. Audio plays.
6.  **Dad presses "I HEARD IT":**
      * Audio stops.
      * App calculates new `targetTime` (Current Time + 2 hours).
      * App schedules the new alarm.
      * `ReminderActivity` closes (`finish()`).

### Next Step

Would you like me to generate the **Kotlin code** for the `AlarmReceiver` and the `Manifest` file so you can copy-paste the most difficult parts?