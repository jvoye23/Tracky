# App Onboarding User Flows

This document outlines the two primary onboarding scenarios for the freemium app of Tracky, detailing the steps for both the free offline path and the premium subscription path.

---

## 🟡 Scenario A: The user chooses the free offline version
In this case, the user clicks "Close" or "Continue with Free" on the paywall. They are guided **directly into the app without any barriers**.

1. **App Launch & Welcome**: The user opens the app and sees the initial welcome screens (Value Proposition).
2. **Permission Request**: The app requests necessary system permissions (e.g., notifications), including a brief explanation of why they are needed.
3. **Paywall Display**: The screen introduces the subscription models and a list of cards with Pro features that the user gets on top with a Pro account.
4. **Declining the Pro Option**: The user taps the **"X"** (Close) or a button like "Continue with Free" to decline the Pro option".
5. **Direct App Entry**: The user immediately lands on the ProjectOverviewScreen.
6. **Local Data Storage**: The app now runs independently and saves all user inputs exclusively locally on the device.

---

## 🟢 Scenario B: The user subscribes to the Pro version
In this case, the user decides to buy the subscription directly on the paywall. Because the subscription needs to work across multiple devices, the registration follows right after.

1. **App Launch & Welcome**: The user opens the app and sees the initial welcome screens (A Hero headline at the top and a list of cards with the basic features.
2. **Permission Request**: The app requests necessary system permissions.
3. **Paywall Display**: The screen introduces the subscription models and a list of cards with Pro features that the user gets on top with a Pro account.
4. **Purchase Decision**: The user selects a subscription model and taps "Subscribe Now".
5. **In-App Purchase (Payment)**: The native payment window of the app store (Apple App Store / Google Play Store) handled by the RevenueCat SDK opens. The user authorizes the payment.
6. **Account Registration**: After a successful payment, the user will land **immediately** on the RegisterScreen.
7. **Premium App Entry**: The user lands on the dashboard, which is now unlocked as "Pro" and offers full access to all online/cloud features.


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
