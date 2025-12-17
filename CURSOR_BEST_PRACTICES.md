# Cursor Best Practices Guide

## 🎯 **Overview**

Cursor is an AI-powered code editor that combines VS Code's functionality with advanced AI assistance. This guide covers best practices for maximizing productivity with Cursor, especially for Android development.

---

## 🚀 **Core Features & Best Practices**

### **1. Chat (Cmd/Ctrl + L)**

**What it is:** Sidebar chat for asking questions and getting code suggestions.

**Best Practices:**

#### ✅ **Do:**
- **Provide context** - Open relevant files before asking
- **Be specific** - "Fix the alarm not triggering" → "Fix AlarmReceiver not receiving broadcasts when app is killed on Android 10+"
- **Use @ mentions** - Reference specific files: `@MainActivity.kt fix the alarm scheduling`
- **Ask for explanations** - "Explain how this AlarmReceiver works"
- **Request step-by-step** - "Break this into steps: research → design → implement"

#### ❌ **Don't:**
- Ask vague questions without context
- Make large changes without testing
- Ignore file context (always open relevant files)

**Example:**
```
❌ BAD:
"Fix the alarm"

✅ GOOD:
"@AlarmReceiver.kt @MainActivity.kt
AlarmReceiver should launch ReminderActivity when alarm triggers,
but it's not working when app is killed on Android 13.
Research Android 13 background restrictions for BroadcastReceivers,
then implement a fix with proper error handling."
```

---

### **2. Composer (Cmd/Ctrl + I)**

**What it is:** Inline editing mode for making changes directly in your code.

**Best Practices:**

#### ✅ **Do:**
- **Select code first** - Highlight the code you want to change
- **Be specific** - "Add error handling to this method"
- **Use natural language** - "Make this function async" or "Add null checks here"
- **Review changes** - Always review before accepting
- **Make incremental changes** - Change one thing at a time

#### ❌ **Don't:**
- Select entire files (too broad)
- Make multiple unrelated changes at once
- Accept changes without reviewing

**Example:**
```
✅ GOOD WORKFLOW:
1. Select the method you want to change
2. Cmd/Ctrl + I
3. "Add null check for mediaPlayer and proper error handling"
4. Review the diff
5. Accept or modify
```

---

### **3. Codebase Indexing**

**What it is:** Cursor indexes your codebase to understand context.

**Best Practices:**

#### ✅ **Do:**
- **Let it index** - Wait for indexing to complete (first time)
- **Use .cursorignore** - Exclude build folders, node_modules, etc.
- **Keep code organized** - Better structure = better AI understanding

#### **Create `.cursorignore`:**
```
# Build outputs
app/build/
build/
.gradle/

# Dependencies
node_modules/

# Generated files
*.class
*.dex
```

---

### **4. Multi-File Editing**

**What it is:** Cursor can edit multiple files in one request.

**Best Practices:**

#### ✅ **Do:**
- **Reference files explicitly** - Use @ mentions
- **Explain relationships** - "Update MainActivity and AlarmScheduler to add quiet hours feature"
- **Review all changes** - Check each file's diff

**Example:**
```
"@MainActivity.kt @AlarmScheduler.kt @SharedPrefsManager.kt
Add quiet hours feature:
1. Add quiet hours settings in MainActivity
2. Store quiet hours in SharedPrefsManager
3. Check quiet hours in AlarmScheduler before scheduling
4. Skip alarms during quiet hours"
```

---

## 📁 **File Management Best Practices**

### **1. Open Relevant Files**

**Before asking Cursor:**
- Open files related to your question
- Cursor uses open files for context
- More context = better answers

**Example:**
```
To fix alarm issue:
1. Open AlarmReceiver.kt
2. Open AlarmScheduler.kt
3. Open MainActivity.kt
4. Then ask: "Why isn't alarm triggering?"
```

---

### **2. Use File References**

**In Chat:**
```
@AlarmReceiver.kt - Reference specific file
@MainActivity.kt:50-100 - Reference specific lines
```

**Benefits:**
- Cursor focuses on relevant code
- More accurate suggestions
- Better context understanding

---

### **3. Organize Your Workspace**

**Best Practices:**
- Keep related files open in tabs
- Use split view for comparing files
- Close unused files (reduces noise)

---

## 💬 **Effective Prompting in Cursor**

### **1. Context-Rich Prompts**

#### ✅ **Good Prompt Structure:**
```
Context: [What you're working on]
Problem: [What's wrong or what you need]
Current State: [What exists now]
Requirements: [What should happen]
Constraints: [Android versions, devices, etc.]
```

**Example:**
```
Context: Alarm reminder app
Problem: Alarm doesn't trigger when app is killed
Current State: AlarmReceiver uses AlarmManager.setExactAndAllowWhileIdle()
Requirements: Alarm must work even when app is force-stopped
Constraints: Android 7.0+ (API 24+), especially Android 13+

Please:
1. Research Android background restrictions
2. Check if AlarmReceiver needs special configuration
3. Implement fix with Android version checks
```

---

### **2. Use Cursor's Codebase Understanding**

**Cursor knows your codebase, so:**
- Reference existing patterns: "Use the same pattern as AlarmScheduler"
- Ask about relationships: "How does MainActivity interact with AlarmScheduler?"
- Request consistency: "Make this consistent with ReminderActivity"

---

### **3. Iterative Development**

**Don't ask for everything at once:**
```
❌ BAD:
"Rewrite the entire alarm system, add quiet hours, change theme, and add Chinese language"

✅ GOOD:
Step 1: "Research quiet hours implementation for alarm apps"
Step 2: "Design quiet hours feature for our alarm system"
Step 3: "Implement quiet hours in AlarmScheduler"
Step 4: "Add UI for quiet hours in SettingsActivity"
```

---

## 🔧 **Cursor Settings & Configuration**

### **1. Recommended Settings**

**Open Settings (Cmd/Ctrl + ,) and configure:**

```json
{
  // AI Settings
  "cursor.ai.enabled": true,
  "cursor.ai.model": "gpt-4", // or "claude" based on preference
  
  // Code Completion
  "cursor.autocomplete.enabled": true,
  "cursor.autocomplete.delay": 100,
  
  // Chat Settings
  "cursor.chat.maxTokens": 4000,
  "cursor.chat.temperature": 0.7,
  
  // File Indexing
  "cursor.indexing.enabled": true,
  "cursor.indexing.maxFileSize": 1000000,
  
  // Editor Settings
  "editor.inlineSuggest.enabled": true,
  "editor.suggestSelection": "first",
  "editor.tabSize": 4,
  
  // Android Specific
  "files.exclude": {
    "**/build": true,
    "**/.gradle": true
  }
}
```

---

### **2. Keyboard Shortcuts**

**Essential Shortcuts:**

| Action | Mac | Windows/Linux |
|--------|-----|---------------|
| Open Chat | `Cmd + L` | `Ctrl + L` |
| Open Composer | `Cmd + I` | `Ctrl + I` |
| Accept Suggestion | `Tab` | `Tab` |
| Reject Suggestion | `Esc` | `Esc` |
| Show Diff | `Cmd + Shift + D` | `Ctrl + Shift + D` |
| Quick Fix | `Cmd + .` | `Ctrl + .` |

**Customize in:** Settings → Keyboard Shortcuts

---

## 🎯 **Workflow Best Practices**

### **1. Development Workflow**

#### **Phase 1: Planning**
```
1. Open relevant files
2. Ask Cursor: "Explain how [feature] currently works"
3. Ask: "What would be needed to add [new feature]?"
4. Review the plan
```

#### **Phase 2: Research**
```
1. Ask: "Research [topic] for Android development"
2. Ask: "What are the Android version compatibility concerns?"
3. Review findings
```

#### **Phase 3: Implementation**
```
1. Use Composer (Cmd/Ctrl + I) for small changes
2. Use Chat for larger changes
3. Test incrementally
4. Ask for explanations if needed
```

#### **Phase 4: Review**
```
1. Ask: "Review this code for bugs and improvements"
2. Ask: "Are there any Android version compatibility issues?"
3. Test thoroughly
```

---

### **2. Debugging Workflow**

#### **Step 1: Understand the Problem**
```
1. Open relevant files
2. Ask: "Explain how this code works"
3. Ask: "What could cause [symptom]?"
```

#### **Step 2: Diagnose**
```
1. Check logs
2. Ask: "Based on this error log, what's the issue?"
3. Ask: "What Android restrictions might apply here?"
```

#### **Step 3: Fix**
```
1. Use Composer to fix the issue
2. Ask: "Fix this issue with proper error handling"
3. Review the fix
```

#### **Step 4: Verify**
```
1. Ask: "Are there edge cases I should test?"
2. Test the fix
3. Ask: "How can I prevent this issue in the future?"
```

---

### **3. Code Review Workflow**

**Before committing:**
```
1. Ask: "Review this code for:
   - Bugs
   - Android version compatibility
   - Performance issues
   - Best practices
   - Security concerns"
2. Review suggestions
3. Apply fixes
```

---

## 📝 **Android-Specific Best Practices**

### **1. Manifest Changes**

**When editing AndroidManifest.xml:**
```
1. Open AndroidManifest.xml
2. Ask: "Add [permission/component] with proper configuration for Android [version]+"
3. Review the changes
4. Verify in documentation
```

**Example:**
```
"@AndroidManifest.xml
Add USE_FULL_SCREEN_INTENT permission with proper configuration
for Android 10+ (API 29+). Include Android 14+ (API 34+) requirements."
```

---

### **2. Gradle Configuration**

**When editing build.gradle.kts:**
```
1. Open build.gradle.kts
2. Ask: "Add [dependency/configuration] for [purpose]"
3. Review version compatibility
4. Sync Gradle
```

---

### **3. Kotlin Code**

**Best practices:**
- Ask for null safety: "Add proper null checks to this function"
- Ask for coroutines: "Make this function use coroutines for async operations"
- Ask for Android best practices: "Refactor this to follow Android best practices"

---

## 🎨 **UI Development Best Practices**

### **1. Jetpack Compose**

**When working with Compose:**
```
1. Open the Compose file
2. Ask: "Add [UI element] following Material 3 design"
3. Ask: "Make this responsive for different screen sizes"
4. Preview changes
```

**Example:**
```
"@MainActivity.kt
Add a settings button in the top-right corner using Material 3 IconButton.
Make it accessible with proper content description."
```

---

### **2. XML Layouts**

**When editing XML:**
```
1. Open the layout file
2. Ask: "Add [element] with proper constraints/attributes"
3. Preview in design view
```

---

## 🔍 **Advanced Tips**

### **1. Use Cursor for Documentation**

**Generate documentation:**
```
"@AlarmScheduler.kt
Generate documentation for this class:
- Class purpose
- Method descriptions
- Usage examples
- Android version requirements"
```

---

### **2. Refactoring**

**Safe refactoring:**
```
1. Ask: "Refactor [component] to [improvement]"
2. Review all changes
3. Test thoroughly
4. Ask: "Are there any breaking changes?"
```

---

### **3. Testing**

**Generate tests:**
```
"@AlarmScheduler.kt
Generate unit tests for this class covering:
- Normal operation
- Edge cases
- Error handling
- Android version differences"
```

---

## ⚠️ **Common Mistakes to Avoid**

### **1. Over-Reliance on AI**
❌ **Don't:** Accept all suggestions without review
✅ **Do:** Review, understand, and verify all changes

### **2. Vague Prompts**
❌ **Don't:** "Fix this"
✅ **Do:** "Fix [specific issue] in [specific file] by [specific approach]"

### **3. Large Changes**
❌ **Don't:** "Rewrite the entire app"
✅ **Do:** Break into smaller, incremental changes

### **4. Ignoring Context**
❌ **Don't:** Ask without opening relevant files
✅ **Do:** Open files, provide context, then ask

### **5. Not Testing**
❌ **Don't:** Accept changes without testing
✅ **Do:** Test after each change, especially for Android

---

## 🎯 **Quick Reference: Cursor Workflow**

### **For New Features:**
```
1. Open relevant files
2. Chat: "Research [feature] for Android"
3. Chat: "Design [feature] for our app"
4. Composer: Implement incrementally
5. Chat: "Review this implementation"
6. Test
```

### **For Bug Fixes:**
```
1. Open relevant files
2. Chat: "Explain how this code works"
3. Chat: "What could cause [symptom]?"
4. Composer: Fix the issue
5. Chat: "Review this fix"
6. Test
```

### **For Refactoring:**
```
1. Open file to refactor
2. Chat: "Review this code for improvements"
3. Chat: "Refactor [specific part] to [improvement]"
4. Review all changes
5. Test
```

---

## 📊 **Productivity Tips**

### **1. Use Snippets**
- Create code snippets for common patterns
- Ask Cursor: "Create a snippet for [pattern]"

### **2. Use Workspace Settings**
- Configure project-specific settings
- Use `.vscode/settings.json` for team consistency

### **3. Use Git Integration**
- Review diffs before committing
- Ask Cursor: "Review this commit for issues"

### **4. Use Extensions**
- Install Android-specific extensions
- Use Cursor with Android Studio tools when needed

---

## 🔄 **Integration with Your Development Process**

### **Based on Your Pee Reminder Experience:**

#### **Phase 1: Research (Use Chat)**
```
"Research full-screen alarm options for Android 10+"
"Check Android 14+ full-screen intent requirements"
```

#### **Phase 2: Design (Use Chat)**
```
"Design quiet hours feature for our alarm system"
"Create technical design document"
```

#### **Phase 3: Implementation (Use Composer)**
```
Select code → Cmd/Ctrl + I → "Add quiet hours check"
```

#### **Phase 4: Review (Use Chat)**
```
"Review this implementation for bugs and Android compatibility"
```

---

## 💡 **Pro Tips**

### **1. Use Cursor for Learning**
```
"Explain how AlarmManager works"
"Explain the difference between setExact() and setExactAndAllowWhileIdle()"
```

### **2. Use Cursor for Code Review**
```
"Review this code for:
- Android best practices
- Potential bugs
- Performance issues
- Security concerns"
```

### **3. Use Cursor for Documentation**
```
"Generate README for this feature"
"Create API documentation for this class"
```

### **4. Use Cursor for Debugging**
```
"Based on this error, what's the issue?"
"What Android restrictions might cause this?"
```

---

## 🎓 **Learning Path**

### **Week 1: Basics**
- Learn keyboard shortcuts
- Practice Chat and Composer
- Get comfortable with file references

### **Week 2: Workflows**
- Develop your workflow
- Practice incremental changes
- Learn to provide good context

### **Week 3: Advanced**
- Multi-file editing
- Codebase understanding
- Advanced prompting

### **Ongoing:**
- Refine your prompts
- Build your workflow
- Share learnings with team

---

## 📚 **Resources**

### **Cursor Documentation**
- Official Cursor docs
- Keyboard shortcuts reference
- Best practices guide

### **Community**
- Cursor Discord
- Reddit r/cursor
- GitHub discussions

---

## ✅ **Checklist: Are You Using Cursor Effectively?**

- [ ] I provide context before asking questions
- [ ] I open relevant files before asking
- [ ] I use @ mentions for file references
- [ ] I review all AI suggestions before accepting
- [ ] I test changes incrementally
- [ ] I ask for explanations when needed
- [ ] I use Chat for research and design
- [ ] I use Composer for small, focused changes
- [ ] I break large changes into smaller ones
- [ ] I verify Android version compatibility

---

## 🎯 **Summary**

**Cursor Best Practices:**
1. **Context is key** - Always provide context
2. **Incremental changes** - Small, testable changes
3. **Review everything** - Don't blindly accept
4. **Use the right tool** - Chat for research, Composer for edits
5. **Learn as you go** - Understand what Cursor suggests
6. **Test thoroughly** - Especially for Android
7. **Iterate** - Refine your prompts and workflow

**Remember:** Cursor is a powerful tool, but you're still the developer. Use it to enhance your productivity, not replace your understanding.

---

*Based on Pee Reminder development experience*
*Created: December 2025*

