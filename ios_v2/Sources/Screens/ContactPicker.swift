import UIKit

/// 模拟来电的完整通讯录选择器。用 UITableView 而不是 UIAlertController，
/// 避免 iOS actionSheet 在联系人多时只露出几行、看起来像“只有四个可选”。
final class ContactPickerViewController: UIViewController, UITableViewDataSource, UITableViewDelegate,
                                          UISearchBarDelegate {
    private let contacts: [CallSetupScreen.Contact]
    private var filtered: [CallSetupScreen.Contact]
    private let onPick: (CallSetupScreen.Contact) -> Void
    private let table = UITableView(frame: .zero, style: .plain)
    private let search = UISearchBar()

    init(contacts: [CallSetupScreen.Contact],
         onPick: @escaping (CallSetupScreen.Contact) -> Void) {
        self.contacts = contacts
        self.filtered = contacts
        self.onPick = onPick
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        title = "选择微信联系人"
        let edge = UIScreenEdgePanGestureRecognizer(target: self,
                                                     action: #selector(edgeBack(_:)))
        edge.edges = .left
        view.addGestureRecognizer(edge)

        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: "取消", style: .plain, target: self, action: #selector(cancel))

        search.placeholder = "搜索联系人"
        search.delegate = self
        search.searchBarStyle = .minimal
        search.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(search)

        table.dataSource = self
        table.delegate = self
        table.rowHeight = 64
        table.keyboardDismissMode = .onDrag
        table.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(table)

        NSLayoutConstraint.activate([
            search.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            search.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 8),
            search.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -8),

            table.topAnchor.constraint(equalTo: search.bottomAnchor),
            table.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            table.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            table.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    @objc private func cancel() {
        (navigationController ?? self).dismiss(animated: true)
    }

    @objc private func edgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended,
              gesture.translation(in: view).x > 60 else { return }
        guard EdgeBackCenter.shouldHandle() else { return }
        handleEdgeBack()
    }

    @objc func handleEdgeBack() {
        cancel()
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        filtered.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let reuse = "ContactCell"
        let cell = tableView.dequeueReusableCell(withIdentifier: reuse)
            ?? UITableViewCell(style: .default, reuseIdentifier: reuse)
        let contact = filtered[indexPath.row]
        cell.textLabel?.text = contact.name
        cell.textLabel?.font = .systemFont(ofSize: 17)
        cell.imageView?.contentMode = .scaleAspectFill
        cell.imageView?.layer.cornerRadius = 20
        cell.imageView?.layer.masksToBounds = true
        let side: CGFloat = 40
        if let avatar = contact.avatar {
            cell.imageView?.image = Self.round(avatar, side: side)
        } else {
            cell.imageView?.image = Self.initial(contact.name, side: side)
        }
        cell.accessoryType = .disclosureIndicator
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let contact = filtered[indexPath.row]
        dismiss(animated: true) { [onPick] in onPick(contact) }
    }

    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        let keyword = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        filtered = keyword.isEmpty ? contacts : contacts.filter { $0.name.contains(keyword) }
        table.reloadData()
    }

    func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
        searchBar.resignFirstResponder()
    }

    private static func round(_ image: UIImage, side: CGFloat) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
        return renderer.image { _ in
            UIBezierPath(ovalIn: CGRect(x: 0, y: 0, width: side, height: side)).addClip()
            image.draw(in: CGRect(x: 0, y: 0, width: side, height: side))
        }
    }

    private static func initial(_ name: String, side: CGFloat) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
        return renderer.image { _ in
            UIColor(white: 0.88, alpha: 1).setFill()
            UIBezierPath(ovalIn: CGRect(x: 0, y: 0, width: side, height: side)).fill()
            let text = String(name.prefix(1)) as NSString
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 17, weight: .medium),
                .foregroundColor: UIColor.darkGray,
            ]
            let size = text.size(withAttributes: attrs)
            text.draw(at: CGPoint(x: (side - size.width) / 2, y: (side - size.height) / 2),
                      withAttributes: attrs)
        }
    }
}
