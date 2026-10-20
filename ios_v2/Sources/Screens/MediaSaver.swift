import UIKit
import Photos

/// 把解析结果直接存进系统相册（视频 / 图集）。
enum MediaSaver {
    enum SaveError: LocalizedError {
        case denied
        case badURL
        case network(String)
        case photos(String)

        var errorDescription: String? {
            switch self {
            case .denied: return "没有相册写入权限，请在系统设置里允许"
            case .badURL: return "成品地址无效"
            case .network(let message): return message
            case .photos(let message): return message
            }
        }
    }

    static func saveVideo(urlString: String,
                          completion: @escaping (Result<String, Error>) -> Void) {
        guard let url = URL(string: urlString) else {
            completion(.failure(SaveError.badURL))
            return
        }
        requestPermission { allowed in
            guard allowed else {
                completion(.failure(SaveError.denied))
                return
            }
            URLSession.shared.downloadTask(with: url) { temp, _, error in
                if let error {
                    DispatchQueue.main.async {
                        completion(.failure(SaveError.network(error.localizedDescription)))
                    }
                    return
                }
                guard let temp else {
                    DispatchQueue.main.async {
                        completion(.failure(SaveError.network("下载失败")))
                    }
                    return
                }
                let target = FileManager.default.temporaryDirectory
                    .appendingPathComponent("doutu-\(UUID().uuidString).mp4")
                do {
                    try? FileManager.default.removeItem(at: target)
                    try FileManager.default.moveItem(at: temp, to: target)
                } catch {
                    DispatchQueue.main.async {
                        completion(.failure(SaveError.network(error.localizedDescription)))
                    }
                    return
                }
                PHPhotoLibrary.shared().performChanges({
                    let request = PHAssetCreationRequest.forAsset()
                    request.addResource(with: .video, fileURL: target, options: nil)
                }) { success, error in
                    try? FileManager.default.removeItem(at: target)
                    DispatchQueue.main.async {
                        if success {
                            completion(.success("相册"))
                        } else {
                            completion(.failure(SaveError.photos(
                                error?.localizedDescription ?? "保存到相册失败")))
                        }
                    }
                }
            }.resume()
        }
    }

    static func saveImages(urlStrings: [String],
                           completion: @escaping (Result<String, Error>) -> Void) {
        requestPermission { allowed in
            guard allowed else {
                completion(.failure(SaveError.denied))
                return
            }
            let group = DispatchGroup()
            let lock = NSLock()
            var images: [UIImage] = []
            var firstError: Error?
            for value in urlStrings {
                guard let url = URL(string: value) else { continue }
                group.enter()
                URLSession.shared.dataTask(with: url) { data, _, error in
                    lock.lock()
                    if let data, let image = UIImage(data: data) {
                        images.append(image)
                    } else if firstError == nil {
                        firstError = SaveError.network(error?.localizedDescription ?? "图片下载失败")
                    }
                    lock.unlock()
                    group.leave()
                }.resume()
            }
            group.notify(queue: .main) {
                if images.isEmpty {
                    completion(.failure(firstError ?? SaveError.network("没有可保存的图片")))
                    return
                }
                PHPhotoLibrary.shared().performChanges({
                    for image in images {
                        PHAssetChangeRequest.creationRequestForAsset(from: image)
                    }
                }) { success, error in
                    DispatchQueue.main.async {
                        if success {
                            completion(.success("相册（\(images.count) 张）"))
                        } else {
                            completion(.failure(SaveError.photos(
                                error?.localizedDescription ?? "保存到相册失败")))
                        }
                    }
                }
            }
        }
    }

    private static func requestPermission(_ completion: @escaping (Bool) -> Void) {
        let handler: (PHAuthorizationStatus) -> Void = { status in
            if #available(iOS 14, *) {
                completion(status == .authorized || status == .limited)
            } else {
                completion(status == .authorized)
            }
        }
        if #available(iOS 14, *) {
            PHPhotoLibrary.requestAuthorization(for: .addOnly, handler: handler)
        } else {
            PHPhotoLibrary.requestAuthorization(handler)
        }
    }
}
