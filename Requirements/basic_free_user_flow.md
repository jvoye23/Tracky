# Basic Free User Flow

This document outlines the user flow when a basic free user wants to access a Pro feature from the Tracky app. In reference to onboarding_user_flow.md, this document outlines
the different feature set of a basic free user and a Pro user.

---
## 🟢 Basic Features
Here are the features of the basic version that the app offers:

1. **Offline Data Storage**: The app runs independently and saves all user data exclusively locally on the device.
2. **Create up to 5 projects**: Create up to 5 independent projects to track your time.
3. **Create tasks for each project**: Create unlimited tasks for each of your projects
4. **Track your projects in the background**: Keep tracking your projects in the background even when the app is closed.
5. **Reorder your projects**: Recorder your projects in the order you want.
6. **Calender view**: Use the calender view for seeing your tracked time in a more organized way.
7. **Detailed stats**: See detailed stats of your tracked tasks


## 🟢 Pro Features
Here are the main features of the Pro version that the app offers:

1. **Data sync with Cloud server**: Sync all your project data with the cloud server in realtime.
2. **Time tracking across devices**: Sync your timers up to 3 devices in real time.
3. **Unlimited amount of projects**: Create an unlimited amount of projects.
4. **Create subtasks**: Create an unlimited amount of subtasks for your project tasks.
5. **Archive projects**: Move your unused projects to the archive.
6. **Export your project**: Export and share your project statistics as PDF or Json format.
7. **Detailed stats**: See detailed stats of your tracked tasks and subtasks
8. **Pin your projects**: Pin your most used projects to the top of your overview

---

## 🟢 Basic free user - user flow


1. **Offline Data Storage**: The app runs independently and saves all user data exclusively locally on the device.
Offline Data storage and no account registration is already in place. Once the basic free user decides to purchase an account
all locally saved data must be synced to the backend

2. **Create up to 5 projects**: Create up to 5 independent projects to track your time.
If the basic free has already created 5 projects they can't create more. An alert dialog must be show that the maximum number
of projects has been reached. Set the title of the Alert Dialog to: "Maximum number of projects reached" and the description text to
"You have reached the maximum number of projects that can be created for a basic user. If you want to create more projects please subscribe to a Pro tier"
Two buttons for the alert dialog: "Show subscription offers" which shall navigate to the PayWallScreen and "Cancel" which closes the alert dialog.

3. **Create tasks for each project**: Create unlimited tasks for each of your projects
Same behavior as a Pro user.

4. **Track your projects in the background**: Keep tracking your projects in the background even when the app is closed.
Same behavior as a Pro user.

5. **Reorder your projects**: Recorder your projects in the order you want.
Same behavior as a Pro user.

6. **Calender view**: Use the calender view for seeing your tracked time in a more organized way.
Same behavior as a Pro user.

7. **Detailed stats**: See detailed stats of your tracked tasks
Same behavior as a Pro user.

8. **Create subtasks**:
When the basic free user clicks on Create Subtask, he shall be navigated to the PayWallScreen.

9. **Export your project**:
When the basic free user clicks on Export in TopAppBar, he shall be navigated to the PayWallScreen.

10. **Archive your project**:
When the basic free user clicks on Archive in TopAppBar, he shall be navigated to the PayWallScreen.

11. **Pin your project**:
When the basic free user clicks on Pin in TopAppBar, he shall be navigated to the PayWallScreen.
