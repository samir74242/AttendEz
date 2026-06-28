import SwiftUI
import Combine
import Foundation

// Local storage model mimicking the Android Room/SharedPreferences structures
struct LocalCourse: Identifiable, Codable {
    var id = UUID()
    var name: String
    var attended: Int
    var missed: Int
    var targetPercentage: Double = 75.0
    
    init(id: UUID = UUID(), name: String, attended: Int, missed: Int, targetPercentage: Double = 75.0) {
        self.id = id
        self.name = name
        self.attended = attended
        self.missed = missed
        self.targetPercentage = targetPercentage
    }
    
    var totalClasses: Int {
        return attended + missed
    }
    
    var attendanceRate: Double {
        guard totalClasses > 0 else { return 0.0 }
        return (Double(attended) / Double(totalClasses)) * 100.0
    }
    
    var statusMessage: String {
        let rate = attendanceRate
        if rate >= targetPercentage {
            return "On Track! Keep it up. 👍"
        } else {
            let req = Int(ceil((targetPercentage * Double(totalClasses) - 100.0 * Double(attended)) / (100.0 - targetPercentage)))
            return "Requires \(max(1, req)) consecutive attendances! ⚠️"
        }
    }
}

struct ContentView: View {
    @State private var courses: [LocalCourse] = [
        LocalCourse(name: "🎓 CS-401 Software Design", attended: 12, missed: 2),
        LocalCourse(name: "📐 MATH-302 Linear Algebra", attended: 9, missed: 4),
        LocalCourse(name: "⚡ EE-210 Network Analysis", attended: 15, missed: 1)
    ]
    
    @State private var showingAddSheet = false
    @State private var newCourseName = ""
    @State private var showBiometricsModal = false
    @State private var selectedCourseIndex: Int?
    @State private var biometricsStatus = "Scanning..."
    @State private var currentTab = 0
    @State private var totalScore = 1500 // Gamified AttendEz points
    
    var body: some View {
        TabView(selection: $currentTab) {
            // Tab 1: Dashboard Tracker
            NavigationView {
                ZStack {
                    Color(red: 0.05, green: 0.08, blue: 0.12).ignoresSafeArea() // Cosmic Slate Theme
                    
                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            // Header Stats
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("AttendEz Dashboard")
                                        .font(.title2)
                                        .fontWeight(.bold)
                                        .foregroundColor(.white)
                                    Text("Keep your academic streak alive!")
                                        .font(.subheadline)
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                // Gamified points badge
                                HStack(spacing: 4) {
                                    Text("✨")
                                    Text("\(totalScore) pts")
                                        .font(.caption)
                                        .fontWeight(.bold)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.yellow.opacity(0.15))
                                .cornerRadius(12)
                                .foregroundColor(.yellow)
                            }
                            .padding(.top, 10)
                            
                            // Weekly insights banner
                            VStack(alignment: .leading, spacing: 6) {
                                Text("WEEKLY REFLECTION 📈")
                                    .font(.caption)
                                    .fontWeight(.bold)
                                    .foregroundColor(.blue)
                                Text("Your overall attendance sits at 89.4%. You are safely meeting limits for all courses this week!")
                                    .font(.footnote)
                                    .foregroundColor(.white.opacity(0.9))
                            }
                            .padding(16)
                            .background(Color.blue.opacity(0.15))
                            .cornerRadius(16)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.blue.opacity(0.3), lineWidth: 1)
                            )
                            
                            // Active course list
                            Text("YOUR COURSES")
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(.gray)
                                .padding(.top, 6)
                            
                            ForEach(courses.indices, id: \.self) { index in
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack {
                                        Text(courses[index].name)
                                            .font(.headline)
                                            .foregroundColor(.white)
                                        Spacer()
                                        Text("\(String(format: "%.1f", courses[index].attendanceRate))%")
                                            .font(.subheadline)
                                            .fontWeight(.black)
                                            .foregroundColor(courses[index].attendanceRate >= courses[index].targetPercentage ? .green : .red)
                                    }
                                    
                                    // Progress bar
                                    GeometryReader { geo in
                                        ZStack(alignment: .leading) {
                                            Rectangle()
                                                .frame(width: geo.size.width, height: 8)
                                                .opacity(0.15)
                                                .foregroundColor(.gray)
                                            
                                            Rectangle()
                                                .frame(width: geo.size.width * CGFloat(min(1.0, courses[index].attendanceRate / 100.0)), height: 8)
                                                .foregroundColor(courses[index].attendanceRate >= courses[index].targetPercentage ? .green : .red)
                                        }
                                        .cornerRadius(4)
                                    }
                                    .frame(height: 8)
                                    
                                    // Attend/Miss counters
                                    HStack(spacing: 12) {
                                        Text("Attended: \(courses[index].attended)")
                                            .font(.caption)
                                            .foregroundColor(.green)
                                        Text("Missed: \(courses[index].missed)")
                                            .font(.caption)
                                            .foregroundColor(.red)
                                        Spacer()
                                        
                                        // Quick buttons
                                        Button(action: {
                                            courses[index].attended += 1
                                            totalScore += 25
                                        }) {
                                            Text("+ Attendance")
                                                .font(.caption2)
                                                .fontWeight(.bold)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color.green.opacity(0.15))
                                                .cornerRadius(6)
                                                .foregroundColor(.green)
                                        }
                                        
                                        Button(action: {
                                            courses[index].missed += 1
                                        }) {
                                            Text("+ Miss")
                                                .font(.caption2)
                                                .fontWeight(.bold)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color.red.opacity(0.15))
                                                .cornerRadius(6)
                                                .foregroundColor(.red)
                                        }
                                    }
                                    
                                    Text(courses[index].statusMessage)
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                        .italic()
                                        .padding(.top, 2)
                                    
                                    // Simulated Face/Fingerprint Attendance
                                    Button(action: {
                                        selectedCourseIndex = index
                                        biometricsStatus = "Ready for Face ID..."
                                        showBiometricsModal = true
                                    }) {
                                        HStack {
                                            Image(systemName: "faceid")
                                            Text("Biometric Check-In")
                                        }
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(.blue)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .background(Color.blue.opacity(0.12))
                                        .cornerRadius(8)
                                    }
                                    .padding(.top, 4)
                                }
                                .padding(16)
                                .background(Color(red: 0.08, green: 0.12, blue: 0.18))
                                .cornerRadius(16)
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }
                .navigationTitle("")
                .navigationBarHidden(true)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(action: { showingAddSheet = true }) {
                            Image(systemName: "plus.circle.fill")
                                .font(.title3)
                        }
                    }
                }
            }
            .tabItem {
                Label("Tracker", systemImage: "checklist")
            }
            .tag(0)
            
            // Tab 2: Premium AI Insights Setup Placeholder
            NavigationView {
                ZStack {
                    Color(red: 0.05, green: 0.08, blue: 0.12).ignoresSafeArea()
                    
                    VStack(spacing: 24) {
                        Text("✨ AI Analytics Engine")
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        
                        Image(systemName: "sparkles")
                            .font(.system(size: 64))
                            .foregroundColor(.yellow)
                            .padding(.vertical, 10)
                        
                        Text("Predictive Attendance Modeling")
                            .font(.headline)
                            .foregroundColor(.white)
                        
                        Text("The AttendEz iOS application is equipped with direct Vertex AI bindings to calculate future semester scores, extract screenshot timetables dynamically, and run predictive analytics under Apple sandboxes.")
                            .font(.body)
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                        
                        Spacer()
                    }
                    .padding(.top, 40)
                }
                .navigationBarTitleDisplayMode(.inline)
            }
            .tabItem {
                Label("AI Insights", systemImage: "sparkles")
            }
            .tag(1)
        }
        .accentColor(.blue)
        .preferredColorScheme(.dark)
        // Add course sheet
        .sheet(isPresented: $showingAddSheet) {
            NavigationView {
                Form {
                    Section(header: Text("Course Information")) {
                        TextField("Course Name (e.g. CS-101)", text: $newCourseName)
                    }
                }
                .navigationTitle("Add New Course")
                .navigationBarItems(
                    leading: Button("Cancel") { showingAddSheet = false },
                    trailing: Button("Save") {
                        if !newCourseName.isEmpty {
                            courses.append(LocalCourse(name: newCourseName, attended: 0, missed: 0))
                            newCourseName = ""
                            showingAddSheet = false
                        }
                    }
                )
            }
        }
        // Biometrics popup
        .sheet(isPresented: $showBiometricsModal) {
            VStack(spacing: 30) {
                Text("AttendEz Security Check")
                    .font(.headline)
                    .padding(.top, 20)
                
                Image(systemName: "faceid")
                    .font(.system(size: 80))
                    .foregroundColor(.blue)
                    .padding(.vertical, 20)
                
                Text(biometricsStatus)
                    .font(.subheadline)
                    .foregroundColor(.gray)
                
                Button(action: {
                    biometricsStatus = "Verifying..."
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                        biometricsStatus = "Success! Attendance Logged."
                        if let index = selectedCourseIndex {
                            courses[index].attended += 1
                            totalScore += 100
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                            showBiometricsModal = false
                        }
                    }
                }) {
                    Text("Verify with Face ID")
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(width: 220, height: 48)
                        .background(Color.blue)
                        .cornerRadius(12)
                }
                
                Button("Cancel") {
                    showBiometricsModal = false
                }
                .foregroundColor(.red)
                .padding(.bottom, 20)
            }
            .padding()
            .presentationDetentsCompat()
        }
    }
}

extension View {
    @ViewBuilder
    func presentationDetentsCompat() -> some View {
        if #available(iOS 16.0, *) {
            self.presentationDetents(Set([.height(360)]))
        } else {
            self
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
