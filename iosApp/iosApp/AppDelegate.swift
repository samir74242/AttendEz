import UIKit
import FirebaseCore
import FirebaseMessaging
import UserNotifications

class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Initialize Firebase safely
        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let options = FirebaseOptions(contentsOfFile: path) {
            FirebaseApp.configure(options: options)
            print("Firebase successfully initialized from GoogleService-Info.plist!")
        } else {
            print("GoogleService-Info.plist not found in bundle, using programmatic fallback initialization.")
            let options = FirebaseOptions(
                googleAppID: "1:123456789000:ios:a1b2c3d4e5f6g7h8i9j0",
                gcmSenderID: "123456789000"
            )
            options.apiKey = "AIzaSyPLACEHOLDER_REPLACE_WITH_YOUR_IOS_API_KEY_AI_STUDIO"
            options.projectID = "attendez-applet-project-id"
            options.databaseURL = "https://attendez-applet-default-rtdb.firebaseio.com"
            options.storageBucket = "attendez-applet-project-id.appspot.com"
            FirebaseApp.configure(options: options)
            print("Firebase programmatic initialization completed successfully!")
        }
        
        // Configure UNUserNotificationCenter for Local and Remote Notifications
        UNUserNotificationCenter.current().delegate = self
        let authOptions: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(options: authOptions) { granted, error in
            if let error = error {
                print("Error requesting notification auth: \(error)")
            }
            print("Notification permission granted: \(granted)")
        }
        
        application.registerForRemoteNotifications()
        
        // Configure Firebase Messaging Delegate
        Messaging.messaging().delegate = self
        
        // Schedule local weekly reminder / alarms mimicking Android DailyReminderScheduler
        scheduleWeeklyReminder()
        
        return true
    }
    
    // MARK: - Firebase Messaging Delegate
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        print("Firebase Cloud Messaging token: \(String(describing: fcmToken))")
        // Store FCM token locally in UserDefaults for synchronized push notification workflows
        UserDefaults.standard.set(fcmToken, forKey: "FCM_TOKEN_KEY")
    }
    
    // MARK: - Local Alarm Scheduling Setup
    func scheduleWeeklyReminder() {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            // Check if reminder is already scheduled to avoid duplicate registers
            let isScheduled = requests.contains { $0.identifier == "DailyReminderAction" }
            if !isScheduled {
                let content = UNMutableNotificationContent()
                content.title = "AttendEz Tracker ⏰"
                content.body = "Time to log today's course attendances! Tap to open."
                content.sound = .default
                
                // Trigger daily at 8:00 PM matching Android WorkManager/Alarm triggers
                var dateComponents = DateComponents()
                dateComponents.hour = 20
                dateComponents.minute = 0
                
                let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)
                let request = UNNotificationRequest(identifier: "DailyReminderAction", content: content, trigger: trigger)
                
                center.add(request) { error in
                    if let error = error {
                        print("Error scheduling local background trigger: \(error)")
                    } else {
                        print("Background DailyReminder successfully scheduled for 8:00 PM!")
                    }
                }
            }
        }
    }
    
    // MARK: - UserNotificationCenter Delegates
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Display notification while app is in foreground
        completionHandler([[.banner, .list, .sound]])
    }
    
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        // Handle notification click action (routing to specific tabs)
        print("User clicked notification: \(response.notification.request.identifier)")
        completionHandler()
    }
}
