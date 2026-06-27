# AttendEz Cross-Platform & iOS Migration Guide

Welcome to the **AttendEz Cross-Platform** architecture package! This repository is now fully structured for dual Android and iOS compatibility, making it compile-ready for cloud-based build pipelines like **Codemagic**.

To maximize stability and ensure that the local streaming emulator remains 100% active and responsive in Google AI Studio, the existing, production-tested native Android source tree is kept completely intact inside `/app`. Simultaneously, we have added a dedicated, fully configured iOS workspace, dependency manifests, and a universal `codemagic.yaml` pipeline.

---

## 📂 New Cross-Platform Project Topology

The workspace now contains:
*   **`/codemagic.yaml`**: The automated pipeline spec to build both the Android `.apk` and the iOS `.app`/`.ipa` on Mac instances.
*   **`/iosApp/`**: A native iOS/Xcode workspace containing:
    *   **`Podfile`**: Declares iOS dependencies, bringing in Firebase components matching Android (`FirebaseCore`, `FirebaseAuth`, `FirebaseDatabase`, `FirebaseMessaging`, and the Gemini-powered `FirebaseVertexAI` SDK).
    *   **`iosApp/Info.plist`**: Includes platform permission descriptors for Microphones, Cameras, and local FaceID sensors.
    *   **`iosApp/App.swift` & `AppDelegate.swift`**: Swift runtime setup that bootstraps Firebase and configures local calendar notification schedules (iOS AlarmManager counterpart).
    *   **`iosApp/ContentView.swift`**: A SwiftUI user interface reproducing the design language (Cosmic Slate theme), courses tracking, stats, and biometric check-ins of the original Jetpack Compose application.

---

## 🛠️ Step-by-Step Build Instructions

### Option 1: Automated Builds via Codemagic
The project is pre-configured to build without manual intervention on [Codemagic](https://codemagic.io/):
1.  Connect your repository (GitHub, GitLab, Bitbucket) to Codemagic.
2.  Codemagic will automatically detect the **`codemagic.yaml`** at the root of the project.
3.  Under your project dashboard, you will find two workflows:
    *   **AttendEz Android Build**: Builds the release-ready Android application.
    *   **AttendEz iOS Build**: Starts a macOS Apple Silicon VM to build and compile the iOS application under Xcode compiler tools.
4.  *(Optional)* For iOS App Store distribution, upload your Apple Developer Certs (`.p12`) and App Store provisioning profiles inside the **Environment Variables** panel in the Codemagic UI.

### Option 2: Local Compilation on a Mac
If you have a macOS machine, you can run and compile the iOS app locally:
1.  Install CocoaPods on your machine:
    ```bash
    sudo gem install cocoapods
    ```
2.  Navigate to the `/iosApp` directory and install the pod dependencies:
    ```bash
    cd iosApp
    pod install
    ```
3.  Open the newly generated Xcode Workspace file:
    ```bash
    open iosApp.xcworkspace
    ```
4.  Connect your physical iPhone or choose an iOS Simulator from the device list, and click the **Run (Play button)** in Xcode.

---

## 🔄 Technical Architecture Translation Matrix

To transition Android-specific elements directly to a unified Kotlin Multiplatform (KMP) or Compose Multiplatform app, use this translation blueprint:

| Android Native Class / API | Multiplatform Equivalent (KMP) | iOS Native Equivalent | Description / Translation Details |
| :--- | :--- | :--- | :--- |
| **Jetpack Compose UI** (`MainActivity.kt`) | **Compose Multiplatform** (`commonMain`) | **SwiftUI** (`ContentView.swift`) | Move your screens to KMP `commonMain`. Or use SwiftUI for the iOS layer, keeping the design consistent using common layout patterns. |
| **Room SQLite DB** (`com.example.data`) | **Room Multiplatform** (KMP) | **CoreData / SwiftData** | Room 2.7.0+ now supports iOS directly using Kotlin Multiplatform! You can compile your entities and DAOs in KMP `commonMain`. |
| **AlarmManager & Broadcasts** (`SmartClassNotificationScheduler`) | **KMP expect/actual** pattern | **UNUserNotificationCenter** | Create a local notification schedule in Swift, setting specific calendar components (`UNCalendarNotificationTrigger`). |
| **WorkManager** (`DailyReminderWorker.kt`) | **BGTaskScheduler** | **Background Tasks Framework** | iOS background tasks are scheduled via `BGTaskScheduler` in `AppDelegate.swift` under the background fetch/processing capability. |
| **OkHttp Client / Webhooks** (`postFeedbackToGoogleForm`) | **Ktor Client** | **URLSession** | Ktor provides a multiplatform HTTP engine. On iOS, Ktor uses the native `Darwin` engine under the hood. |
| **BiometricPrompt** (`BiometricAttendanceSheet.kt`) | **KMP Biometrics wrapper** | **LocalAuthentication** (`LAContext`) | Swift uses `LAContext().evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics)`. On iOS, ensure `NSFaceIDUsageDescription` is in `Info.plist`. |
| **Firebase AI (Gemini)** (`firebase-ai`) | **Firebase AI SDK** (Multiplatform) | **FirebaseVertexAI** (Swift Package) | Swift packages or Pods connect to Vertex AI on Google Cloud securely without middle tier proxies. |

---

## 🔐 Firebase iOS Configuration Setup
We have included a `GoogleService-Info.plist` template in `/iosApp/iosApp`. To activate cloud integrations for iOS:
1.  Go to your [Firebase Console](https://console.firebase.google.com/).
2.  Click **Add App**, choose **iOS**, and specify the bundle identifier: `com.aistudio.attendez`.
3.  Download the **`GoogleService-Info.plist`** configuration file.
4.  Replace the placeholder file in `iosApp/iosApp/GoogleService-Info.plist` with your downloaded file.
5.  Your iOS client is now securely connected to your shared Firebase Realtime Database and Cloud Messaging systems!
