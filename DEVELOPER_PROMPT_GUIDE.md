# Developer Prompt Guide: How to Get Better Results from AI Coding Assistants

Based on the Pee Reminder development experience, here are proven strategies for effective AI-assisted development.

---

## 🎯 **Core Principles**

### **1. Context First, Code Second**
❌ **Bad Prompt:**
```
"Fix the alarm not working"
```

✅ **Good Prompt:**
```
"I have an Android alarm app that uses AlarmManager. The alarm works when the app is in foreground, but when the app is in background or device is locked, the alarm doesn't trigger. 

Current implementation:
- Using AlarmReceiver (BroadcastReceiver)
- AlarmManager.setExactAndAllowWhileIdle()
- Android 10+ device

What Android restrictions might be blocking this, and what's the recommended solution?"
```

**Why it works:** Provides context, current state, and asks for analysis before implementation.

---

### **2. Research Before Implementation**
❌ **Bad Prompt:**
```
"Make the alarm full-screen when it triggers"
```

✅ **Good Prompt:**
```
"I need to show a full-screen alarm activity when AlarmManager triggers, even when:
- App is in background
- Device is locked
- Device is sleeping

Before implementing, please:
1. Research Android's full-screen intent notifications API
2. Check Android 10+ background activity restrictions
3. Verify Android 14+ permission requirements
4. Provide a technical design document with pros/cons of each approach
5. Then implement the recommended solution"
```

**Why it works:** Asks for research and design first, preventing costly mistakes.

---

### **3. Incremental Changes**
❌ **Bad Prompt:**
```
"Rewrite the entire alarm system to use WorkManager instead"
```

✅ **Good Prompt:**
```
"Current alarm system uses AlarmManager. I'm considering WorkManager as an alternative.

Please:
1. Compare AlarmManager vs WorkManager for exact-time alarms
2. Analyze if WorkManager would work when app is force-stopped
3. If yes, create a small proof-of-concept
4. If no, explain why and suggest improvements to current AlarmManager approach"
```

**Why it works:** Asks for analysis and small POC before major refactoring.

---

## 📋 **Prompt Templates**

### **Template 1: New Feature Request**

```
I want to add [FEATURE] to my [APP_TYPE] app.

Current state:
- [What exists now]
- [Current implementation details]
- [Relevant code files/locations]

Requirements:
- [Must have requirement 1]
- [Must have requirement 2]
- [Nice to have requirement 3]

Constraints:
- [Android version: X+]
- [Target devices: Y]
- [Performance requirements: Z]

Please:
1. Research best practices for this feature
2. Identify potential Android API restrictions
3. Create a technical design document
4. Implement the solution with proper error handling
5. Add comments explaining Android version compatibility
```

**Example:**
```
I want to add quiet hours to my alarm reminder app.

Current state:
- Alarm triggers every 2 hours using AlarmManager
- ReminderActivity shows full-screen alarm
- SharedPrefsManager stores interval and active state

Requirements:
- User can set quiet hours (e.g., 10 PM - 7 AM)
- Alarms should not trigger during quiet hours
- Should automatically reschedule after quiet hours end

Constraints:
- Android 7.0+ (API 24+)
- Must work when app is killed
- Should handle timezone changes

Please:
1. Research best practices for quiet hours in alarm apps
2. Identify how to check if current time is in quiet hours range
3. Create a technical design for quiet hours logic
4. Implement with proper edge case handling (midnight crossover, etc.)
```

---

### **Template 2: Bug Fix Request**

```
I'm experiencing [BUG_DESCRIPTION] in my [APP_TYPE] app.

Symptoms:
- [What happens]
- [When it happens]
- [What should happen instead]

Environment:
- Android version: [X]
- Device: [Y]
- App state when bug occurs: [Z]

Current implementation:
- [Relevant code snippet or file location]
- [How it's supposed to work]

I've tried:
- [Attempt 1]
- [Attempt 2]

Please:
1. Analyze the root cause
2. Check for Android version-specific issues
3. Research similar problems and solutions
4. Provide a fix with explanation
5. Suggest how to prevent this in the future
```

**Example:**
```
I'm experiencing alarm sound not playing in my reminder app.

Symptoms:
- Alarm triggers and screen wakes up
- But no sound plays
- Vibration works fine

Environment:
- Android 13 (API 33)
- Device: Pixel 7
- App state: Background, device was locked

Current implementation:
- ReminderActivity uses MediaPlayer with RingtoneManager
- AudioManager.requestAudioFocus() is called
- Sound works when app is in foreground

I've tried:
- Checking audio focus permissions
- Using different audio stream types

Please:
1. Analyze why audio might not play when device is locked
2. Check Android 13 audio restrictions
3. Research audio focus requirements for alarms
4. Provide a fix with proper audio focus handling
```

---

### **Template 3: Performance Optimization**

```
I want to optimize [COMPONENT/FEATURE] in my [APP_TYPE] app.

Current performance:
- [Current metrics: startup time, memory usage, etc.]
- [Where the bottleneck is]

Target performance:
- [Desired metrics]

Current implementation:
- [Relevant code/files]

Please:
1. Profile the current implementation
2. Identify performance bottlenecks
3. Research Android best practices for this scenario
4. Suggest optimizations with trade-offs
5. Implement the most impactful optimizations
```

---

### **Template 4: Platform Compatibility**

```
I need to ensure [FEATURE] works on [PLATFORM/ROM].

Current status:
- Works on: [Standard Android]
- Doesn't work on: [Custom ROM/Platform]
- Symptoms: [What happens or doesn't happen]

Platform details:
- ROM: [Name and version]
- Known restrictions: [If any]

Please:
1. Research this platform's specific restrictions
2. Identify what's different from standard Android
3. Check if there are workarounds
4. Implement platform-specific handling if needed
5. Create documentation for users on this platform
```

**Example:**
```
I need to ensure alarms work on OriginOS 4 when app is swiped away.

Current status:
- Works on: Standard Android 14
- Doesn't work on: OriginOS 4 (Vivo devices)
- Symptoms: Alarm scheduled but BroadcastReceiver never receives it

Platform details:
- ROM: OriginOS 4
- Known restrictions: Aggressive background app killing

Please:
1. Research OriginOS 4 background restrictions
2. Identify what settings users need to configure
3. Check if there are programmatic workarounds
4. Implement detection and user guidance
5. Create compatibility documentation
```

---

## 🚀 **Advanced Prompting Strategies**

### **Strategy 1: "Research First" Prompt**

When you're unsure about the approach:

```
Before implementing [FEATURE], please:

1. RESEARCH PHASE:
   - Research Android APIs for [FEATURE]
   - Check Android version compatibility (API 24-36)
   - Identify potential restrictions or limitations
   - Find similar implementations or examples

2. ANALYSIS PHASE:
   - Compare different approaches (list pros/cons)
   - Recommend the best approach for my use case
   - Identify edge cases and how to handle them

3. DESIGN PHASE:
   - Create a technical design document
   - Outline the implementation steps
   - List required permissions and configurations

4. IMPLEMENTATION PHASE:
   - Implement the recommended solution
   - Add comprehensive error handling
   - Include Android version checks where needed

5. DOCUMENTATION PHASE:
   - Document the solution
   - Add code comments explaining Android-specific code
   - Create testing checklist
```

---

### **Strategy 2: "Incremental Development" Prompt**

For complex features:

```
I want to implement [COMPLEX_FEATURE] in stages.

STAGE 1 - Proof of Concept:
- Create a minimal working example
- Test core functionality only
- Use simple UI for testing

STAGE 2 - Core Implementation:
- Build the main feature
- Add basic error handling
- Test on primary Android version

STAGE 3 - Compatibility:
- Add Android version checks
- Handle edge cases
- Test on multiple Android versions

STAGE 4 - Polish:
- Improve UI/UX
- Add comprehensive error messages
- Optimize performance

Let's start with STAGE 1. After each stage, I'll test and we'll proceed to the next.
```

---

### **Strategy 3: "Code Review" Prompt**

Before making changes:

```
Please review my [COMPONENT] implementation for:

1. CORRECTNESS:
   - Does it follow Android best practices?
   - Are there any obvious bugs?
   - Will it work on Android 10+?

2. EFFICIENCY:
   - Are there performance issues?
   - Can it be optimized?
   - Memory leaks or resource management issues?

3. MAINTAINABILITY:
   - Is the code readable?
   - Are edge cases handled?
   - Is error handling comprehensive?

4. COMPATIBILITY:
   - Android version compatibility?
   - Device-specific issues?
   - Permission requirements?

Current code:
[PASTE CODE OR FILE PATH]

Please provide:
- Issues found (with severity: Critical/High/Medium/Low)
- Suggested improvements
- Code examples for fixes
```

---

### **Strategy 4: "Debugging" Prompt**

When something isn't working:

```
I'm debugging [ISSUE] in my [APP_TYPE] app.

Problem:
- [What's happening]
- [Expected behavior]
- [When it occurs]

Debugging info:
- Logcat output: [RELEVANT LOGS]
- Stack trace: [IF ANY]
- Device info: [Android version, device model]

Code involved:
- [File paths and line numbers]
- [Relevant code snippets]

Hypothesis:
- [What I think might be wrong]

Please:
1. Analyze the logs and code
2. Identify the root cause
3. Check for Android version-specific issues
4. Provide a fix with explanation
5. Suggest how to prevent similar issues
```

---

## ⚠️ **Common Prompt Mistakes to Avoid**

### **Mistake 1: Vague Requests**
❌ "Fix the alarm"
✅ "Fix alarm not triggering when app is in background on Android 10+"

### **Mistake 2: No Context**
❌ "Add Chinese language"
✅ "Add Chinese language support. Current app uses hardcoded English strings in MainActivity and SettingsActivity. Need to extract to strings.xml and add values-zh/strings.xml"

### **Mistake 3: Too Much at Once**
❌ "Rewrite the entire alarm system, add quiet hours, change theme, and add Chinese language"
✅ Break into separate, focused prompts

### **Mistake 4: No Constraints**
❌ "Make it work everywhere"
✅ "Make it work on Android 7.0+ (API 24+), with special handling for Android 10+ background restrictions"

### **Mistake 5: No Testing Plan**
❌ "Implement this feature"
✅ "Implement this feature and create a testing checklist for different scenarios"

---

## 📊 **Prompt Effectiveness Comparison**

### **Low Effectiveness Prompt:**
```
"Fix alarm"
```
- ❌ No context
- ❌ No current state
- ❌ No requirements
- ❌ Result: Generic, possibly wrong solution

### **Medium Effectiveness Prompt:**
```
"Fix alarm not working when app is closed"
```
- ✅ Identifies the problem
- ❌ No context about current implementation
- ❌ No Android version info
- ⚠️ Result: Might work, but may miss edge cases

### **High Effectiveness Prompt:**
```
"Alarm doesn't trigger when app is swiped away on Android 13.

Current: AlarmManager.setExactAndAllowWhileIdle() with BroadcastReceiver.
Works when app is in foreground, fails when app is killed.

Please:
1. Research Android 13 background restrictions for alarms
2. Check if BroadcastReceiver needs special configuration
3. Verify if we need foreground service or other approach
4. Implement solution with Android version checks
5. Add logging to verify alarm scheduling and triggering"
```
- ✅ Clear problem statement
- ✅ Current implementation context
- ✅ Android version specified
- ✅ Asks for research first
- ✅ Requests comprehensive solution
- ✅ Result: Well-researched, correct solution

---

## 🎓 **Lessons from Pee Reminder Development**

### **What Worked Well:**

1. **Phase 3 Approach:**
   ```
   "Before implementing overlay window, please:
   1. Research full-screen alarm options on Android
   2. Create availability report
   3. Compare different approaches
   4. Then implement recommended solution"
   ```
   ✅ Result: Smooth implementation, good documentation

### **What Didn't Work:**

2. **Phase 2 Approach:**
   ```
   "Make alarm full-screen"
   ```
   ❌ Result: Had to revert, lost work, took 2.5+ hours

---

## 💡 **Quick Reference: Prompt Checklist**

Before asking for help, ensure your prompt includes:

- [ ] **Clear goal** - What do you want to achieve?
- [ ] **Current state** - What exists now?
- [ ] **Problem/Requirement** - What's the issue or need?
- [ ] **Context** - Relevant code, files, Android versions
- [ ] **Constraints** - Android versions, devices, requirements
- [ ] **What you've tried** - Previous attempts (if debugging)
- [ ] **Expected outcome** - What should happen?
- [ ] **Research request** - Ask for research if unsure
- [ ] **Testing plan** - How to verify the solution

---

## 🔄 **Iterative Prompting Strategy**

For complex features, use iterative prompts:

**Iteration 1: Research**
```
"Research [TOPIC] for Android. What are the options, pros/cons, Android version compatibility?"
```

**Iteration 2: Design**
```
"Based on the research, design a solution for [REQUIREMENT]. Create technical design document."
```

**Iteration 3: Implementation**
```
"Implement the solution from the design document. Focus on [SPECIFIC_ASPECT]."
```

**Iteration 4: Testing**
```
"Create a testing checklist for [FEATURE]. What scenarios should we test?"
```

**Iteration 5: Refinement**
```
"Review the implementation. Are there edge cases we missed? Any optimizations?"
```

---

## 📝 **Example: Complete Feature Request**

Here's a complete example of an effective prompt:

```
I want to add battery optimization detection and user guidance to my alarm reminder app.

CURRENT STATE:
- App uses AlarmManager for reminders
- Alarms work when app is in foreground
- Sometimes fail when app is killed (especially on custom ROMs)
- No user guidance about battery optimization settings

REQUIREMENTS:
- Detect if app is battery optimized
- Show user-friendly message if optimized
- Provide one-tap button to open battery optimization settings
- Should work on Android 6.0+ (API 23+)
- Handle different manufacturers (Samsung, Xiaomi, etc.)

CURRENT CODE:
- MainActivity.kt uses Jetpack Compose
- PermissionHelper.kt exists but doesn't have battery optimization methods
- SettingsActivity.kt has permission management UI

CONSTRAINTS:
- Must work on Android 6.0+ (API 23+)
- Different manufacturers have different settings paths
- Should gracefully handle if settings can't be opened

PLEASE:
1. Research Android battery optimization APIs (PowerManager, Settings)
2. Check manufacturer-specific settings paths (Samsung, Xiaomi, etc.)
3. Create a utility class for battery optimization detection
4. Implement UI component for MainActivity showing optimization status
5. Add one-tap button to open settings (with fallback for different manufacturers)
6. Add error handling for unsupported devices
7. Create testing checklist for different Android versions and manufacturers
```

---

## 🎯 **Summary: The Golden Rules**

1. **Context is King** - Always provide current state and relevant code
2. **Research First** - Ask for research/analysis before implementation
3. **Be Specific** - Include Android versions, devices, constraints
4. **Incremental** - Break complex features into smaller prompts
5. **Test Plan** - Always ask for testing checklist
6. **Documentation** - Request documentation for complex solutions

---

*Based on analysis of Pee Reminder development history*
*Created: December 2025*

