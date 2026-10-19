import UIKit

/// 小荷包成员头像：调用 xxapi.cn 随机头像接口，下载图片后交给 PurseStore。
enum PurseAvatarAPI {
    private static let endpoint = URL(string: "https://v2.xxapi.cn/api/head?return=json")!

    static func fetch(completion: @escaping (UIImage?) -> Void) {
        var request = URLRequest(url: endpoint)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        URLSession.shared.dataTask(with: request) { data, _, _ in
            guard let data,
                  let parsed = try? JSONSerialization.jsonObject(with: data),
                  let object = parsed as? [String: Any],
                  (object["code"] as? Int) == 200,
                  let raw = object["data"] as? String,
                  let url = URL(string: raw) else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            URLSession.shared.dataTask(with: url) { imageData, _, _ in
                let image = imageData.flatMap(UIImage.init(data:))
                DispatchQueue.main.async { completion(image) }
            }.resume()
        }.resume()
    }
}
