# OriginOS 4 Alarm Compatibility Report

## Summary

**Question**: Can alarms trigger when the app is swiped away on OriginOS 4?

**Answer**: **It depends on system settings**. OriginOS 4 has aggressive background restrictions that can block AlarmManager broadcasts even when alarms are properly scheduled.

## Current Status

- ✅ **Works on**: Android 14 (API 34+) with standard Android behavior
- ⚠️ **May not work on**: OriginOS 4 if system restrictions are enabled
- ✅ **Alarm scheduling**: Works correctly (alarm is scheduled in system)
- ❌ **Broadcast delivery**: May be blocked by OriginOS 4 when app is killed

## Technical Details

### How AlarmManager Works

1. **Alarm Scheduling**: `AlarmManager.setExactAndAllowWhileIdle()` stores the alarm at the **system level**
   - This works even when the app is killed
   - The alarm is stored in Android's AlarmManager service

2. **Alarm Triggering**: When the alarm time arrives:
   - Android's AlarmManager service fires the alarm
   - It attempts to deliver a broadcast to `AlarmReceiver`
   - **OriginOS 4 may block this broadcast** if the app is force-stopped

3. **BroadcastReceiver**: The `AlarmReceiver` must receive the broadcast
   - Static receivers (in manifest) should work even when app is killed
   - **OriginOS 4 may prevent this** due to aggressive background restrictions

## OriginOS 4 Restrictions

OriginOS 4 can block broadcasts to killed apps even for:
- System-level alarms (AlarmManager)
- Static BroadcastReceivers
- Boot receivers

This is a **device/ROM limitation**, not an app bug.

## Required Settings for OriginOS 4

To make alarms work when the app is swiped away on OriginOS 4, users must:

### 1. Battery Optimization
- **Settings → Apps → Pee Reminder → Battery → Don't optimize**
- **Settings → Battery → Battery Optimization → Pee Reminder → Don't Optimize**

### 2. Background Activity
- **Settings → Apps → Pee Reminder → Allow background activity**
- **Settings → Battery → Background power consumption → Don't restrict**

### 3. Auto-Start (if available)
- **Settings → Apps → Pee Reminder → Auto-start → Enable**
- Note: This option may not be visible on all OriginOS 4 versions

### 4. Special App Access
- **Settings → Apps → Special app access → Full screen intents → Enable Pee Reminder**
- **Settings → Apps → Special app access → Autostart → Enable Pee Reminder** (if available)

### 5. Notification Settings
- **Settings → Apps → Pee Reminder → Notifications → Allow all**
- **Settings → Notifications & Status Bar → Pee Reminder → Allow all**

## Testing

### How to Test

1. Schedule an alarm (e.g., 1 minute from now)
2. Swipe away the app from recent apps
3. Wait for the alarm time
4. Check Logcat for:
   - `AlarmReceiver: === ALARM RECEIVED ===` → **SUCCESS** (alarm works)
   - No log → **FAILURE** (OriginOS 4 blocked the broadcast)

### Diagnostic Logs

The app logs detailed information:
- Alarm scheduling: `AlarmScheduler: === SCHEDULING ALARM ===`
- Alarm received: `AlarmReceiver: === ALARM RECEIVED ===`
- OriginOS detection: `AlarmReceiver: ⚠️ ORIGINOS 4 DETECTED`

## Limitations

### What We Cannot Control

1. **OriginOS 4 system restrictions**: We cannot bypass ROM-level restrictions programmatically
2. **Force-stop behavior**: If user force-stops the app via Settings, alarms will definitely not work
3. **System updates**: OriginOS updates may change restriction behavior

### What We Can Do

1. ✅ Schedule alarms correctly using `setExactAndAllowWhileIdle()`
2. ✅ Use static BroadcastReceiver in manifest
3. ✅ Provide clear instructions to users
4. ✅ Add diagnostics to detect when broadcasts are blocked
5. ✅ Guide users to required system settings

## Alternative Solutions

### Option 1: Persistent Foreground Service
- Keep a foreground service running
- **Problem**: Can be killed by system or user
- **Not recommended**: Drains battery, user-visible notification

### Option 2: WorkManager
- Use WorkManager for scheduling
- **Problem**: Also gets blocked when app is force-stopped
- **Not recommended**: Less reliable than AlarmManager

### Option 3: System Alarm Clock
- Use system alarm clock APIs
- **Problem**: Requires system-level permissions
- **Not recommended**: Not available to regular apps

## Conclusion

**Is it available?** 

- **Technically**: Yes, AlarmManager alarms can work on OriginOS 4
- **Practically**: Only if users configure the required system settings
- **Reliability**: Lower than standard Android due to ROM restrictions

**Recommendation**: 
- Provide clear instructions to users
- Add diagnostics to detect when alarms are blocked
- Guide users through required settings
- Accept that some users may not be able to configure all settings

## User Instructions

If alarms don't work when the app is swiped away:

1. **Check Logcat**: Look for `AlarmReceiver: === ALARM RECEIVED ===`
2. **If no log appears**: OriginOS 4 is blocking the broadcast
3. **Configure settings**: Follow the "Required Settings" section above
4. **Test again**: Schedule a test alarm and swipe away the app

## References

- Android AlarmManager documentation
- OriginOS 4 background restriction documentation
- Vivo device settings documentation

