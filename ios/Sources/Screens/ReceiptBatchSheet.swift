import UIKit

/// 「批量添加收款记录」—— 对应 Android `ReceiptActivity.showBatchSheet()`。
///
/// Android 是 AlertDialog + CheckBox/EditText；iOS 用同样的底部弹窗布局：
/// 五个档位各一行「勾选 + 区间 + 条数」，再一行自定义区间，最后选月份。
/// 生成逻辑直接调 `ReceiptStore.add(_:year:month:)`，与 Android 同一个数据层。
final class ReceiptBatchSheet: UIViewController {
    private let dim = UIControl()
    private let sheet = UIView()
    private var tierSwitches: [UISwitch] = []
    private var tierCounts: [UITextField] = []
    private let customSwitch = UISwitch()
    private let customMin = UITextField()
    private let customMax = UITextField()
    private let customCount = UITextField()
    private let monthButton = UIButton(type: .system)
    private var monthOptions: [(year: Int, month: Int, label: String)] = []
    private var selectedMonth = 0
    private var fields: [UITextField] = []
    private let onDone: () -> Void

    init(onDone: @escaping () -> Void) {
        self.onDone = onDone
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    static func present(onDone: @escaping () -> Void) {
        guard let host = UpdatePrompt.topViewController() else { return }
        let sheet = ReceiptBatchSheet(onDone: onDone)
        sheet.modalPresentationStyle = .overFullScreen
        sheet.modalTransitionStyle = .crossDissolve
        host.present(sheet, animated: false)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        dim.backgroundColor = UIColor.black.withAlphaComponent(0.45)
        dim.addTarget(self, action: #selector(close), for: .touchUpInside)
        view.addSubview(dim)

        sheet.backgroundColor = UIColor(red: 0xf7 / 255, green: 0xf7 / 255, blue: 0xf9 / 255, alpha: 1)
        sheet.layer.cornerRadius = 20
        sheet.layer.masksToBounds = true
        view.addSubview(sheet)

        let handle = UIView()
        handle.tag = 100
        handle.backgroundColor = UIColor(red: 0xdc / 255, green: 0xdc / 255, blue: 0xe1 / 255, alpha: 1)
        handle.layer.cornerRadius = 2.5
        sheet.addSubview(handle)

        let title = UILabel()
        title.tag = 101
        title.text = "批量添加收款记录"
        title.font = .systemFont(ofSize: 17, weight: .bold)
        title.textColor = UIColor(red: 0x23 / 255, green: 0x25 / 255, blue: 0x2b / 255, alpha: 1)
        sheet.addSubview(title)

        let hint = UILabel()
        hint.tag = 102
        hint.text = "勾选要生成的档位并填上条数，金额在区间内随机（两位小数）"
        hint.font = .systemFont(ofSize: 12.5)
        hint.textColor = UIColor(red: 0x8a / 255, green: 0x8a / 255, blue: 0x8e / 255, alpha: 1)
        hint.numberOfLines = 0
        sheet.addSubview(hint)

        for (index, name) in ReceiptStore.tierNames.enumerated() {
            let range = ReceiptStore.tierRanges[index]
            let row = makeTierRow(index: index, name: name, range: range)
            sheet.addSubview(row)
        }

        let customRow = makeCustomRow()
        customRow.tag = 120
        sheet.addSubview(customRow)
        customSwitch.isOn = false
        customMin.placeholder = "最低"
        customMax.placeholder = "最高"
        customCount.placeholder = "条数"
        [customMin, customMax, customCount].forEach {
            configureField($0)
            customRow.addSubview($0)
        }
        customCount.keyboardType = .numberPad
        customSwitch.tag = 1

        let monthLabel = UILabel()
        monthLabel.tag = 121
        monthLabel.text = "指定月份"
        monthLabel.font = .systemFont(ofSize: 14.5)
        monthLabel.textColor = UIColor(red: 0x23 / 255, green: 0x25 / 255, blue: 0x2b / 255, alpha: 1)
        sheet.addSubview(monthLabel)

        monthButton.tag = 122
        monthButton.titleLabel?.font = .systemFont(ofSize: 14.5)
        monthButton.contentHorizontalAlignment = .left
        monthButton.addTarget(self, action: #selector(chooseMonth), for: .touchUpInside)
        sheet.addSubview(monthButton)
        buildMonths()

        let cancel = UIButton(type: .system)
        cancel.tag = 130
        cancel.setTitle("取消", for: .normal)
        cancel.titleLabel?.font = .systemFont(ofSize: 16)
        cancel.setTitleColor(UIColor(red: 0x57 / 255, green: 0x6b / 255, blue: 0x95 / 255, alpha: 1), for: .normal)
        cancel.addTarget(self, action: #selector(close), for: .touchUpInside)
        sheet.addSubview(cancel)

        let add = UIButton(type: .system)
        add.tag = 131
        add.setTitle("添加", for: .normal)
        add.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        add.setTitleColor(.white, for: .normal)
        add.backgroundColor = UIColor(red: 0x14 / 255, green: 0x78 / 255, blue: 0xfc / 255, alpha: 1)
        add.layer.cornerRadius = 10
        add.addTarget(self, action: #selector(submit), for: .touchUpInside)
        sheet.addSubview(add)

        let tap = UITapGestureRecognizer(target: self, action: #selector(endEditing))
        tap.cancelsTouchesInView = false
        view.addGestureRecognizer(tap)
    }

    private func makeTierRow(index: Int, name: String, range: (Double, Double)) -> UIView {
        let row = UIView()
        row.tag = 110 + index

        let toggle = UISwitch()
        toggle.tag = 1
        toggle.onTintColor = UIColor(red: 0x14 / 255, green: 0x78 / 255, blue: 0xfc / 255, alpha: 1)
        row.addSubview(toggle)
        tierSwitches.append(toggle)

        let label = UILabel()
        label.tag = 2
        label.text = "\(name)（\(bound(range.0)) ~ \(bound(range.1))）"
        label.font = .systemFont(ofSize: 14.5)
        label.textColor = UIColor(red: 0x23 / 255, green: 0x25 / 255, blue: 0x2b / 255, alpha: 1)
        row.addSubview(label)

        let count = UITextField()
        count.tag = 3
        count.placeholder = "条数"
        configureField(count)
        count.keyboardType = .numberPad
        row.addSubview(count)
        tierCounts.append(count)
        return row
    }

    private func makeCustomRow() -> UIView {
        let row = UIView()
        let switchLabel = UILabel()
        switchLabel.tag = 2
        switchLabel.text = "自定义"
        switchLabel.font = .systemFont(ofSize: 14.5)
        switchLabel.textColor = UIColor(red: 0x23 / 255, green: 0x25 / 255, blue: 0x2b / 255, alpha: 1)
        row.addSubview(switchLabel)

        customSwitch.onTintColor = UIColor(red: 0x14 / 255, green: 0x78 / 255, blue: 0xfc / 255, alpha: 1)
        row.addSubview(customSwitch)

        let dash = UILabel()
        dash.tag = 3
        dash.text = "~"
        dash.textColor = UIColor(red: 0x8a / 255, green: 0x8a / 255, blue: 0x8e / 255, alpha: 1)
        dash.textAlignment = .center
        row.addSubview(dash)
        return row
    }

    private func configureField(_ field: UITextField) {
        field.borderStyle = .roundedRect
        field.font = .systemFont(ofSize: 14)
        field.textAlignment = .center
        field.keyboardType = .decimalPad
        field.returnKeyType = .done
        field.delegate = self
        field.backgroundColor = .white
        field.textColor = UIColor(red: 0x23 / 255, green: 0x25 / 255, blue: 0x2b / 255, alpha: 1)
        let toolbar = UIToolbar()
        toolbar.sizeToFit()
        toolbar.items = [
            UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
            UIBarButtonItem(title: "完成", style: .done, target: self, action: #selector(endEditing)),
        ]
        field.inputAccessoryView = toolbar
        fields.append(field)
    }

    private func buildMonths() {
        monthOptions.removeAll()
        var calendar = Calendar.current
        calendar.timeZone = .current
        var components = calendar.dateComponents([.year, .month], from: Date())
        components.day = 1
        var date = calendar.date(from: components) ?? Date()
        for i in 0..<12 {
            let c = calendar.dateComponents([.year, .month], from: date)
            let label = "\(c.year ?? 0)年\(c.month ?? 0)月" + (i == 0 ? " 本月" : "")
            monthOptions.append((c.year ?? 0, c.month ?? 0, label))
            date = calendar.date(byAdding: .month, value: -1, to: date) ?? date
        }
        selectedMonth = 0
        updateMonthTitle()
    }

    private func updateMonthTitle() {
        guard monthOptions.indices.contains(selectedMonth) else { return }
        monthButton.setTitle(monthOptions[selectedMonth].label + "  ▾", for: .normal)
        monthButton.setTitleColor(UIColor(red: 0x14 / 255, green: 0x78 / 255, blue: 0xfc / 255, alpha: 1),
                                  for: .normal)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        dim.frame = view.bounds
        let bottom = view.safeAreaInsets.bottom
        let sheetH = 560 + bottom
        let width = view.bounds.width - 24
        sheet.frame = CGRect(x: 12, y: view.bounds.height - sheetH, width: width, height: sheetH)

        sheet.viewWithTag(100)?.frame = CGRect(x: (width - 38) / 2, y: 10, width: 38, height: 5)
        sheet.viewWithTag(101)?.frame = CGRect(x: 20, y: 28, width: width - 40, height: 24)
        sheet.viewWithTag(102)?.frame = CGRect(x: 20, y: 56, width: width - 40, height: 34)

        let contentW = width - 40
        for i in 0..<tierSwitches.count {
            guard let row = sheet.viewWithTag(110 + i) else { continue }
            row.frame = CGRect(x: 20, y: 96 + CGFloat(i) * 46, width: contentW, height: 46)
            row.viewWithTag(1)?.frame = CGRect(x: 0, y: 8, width: 51, height: 31)
            row.viewWithTag(2)?.frame = CGRect(x: 60, y: 11, width: contentW - 150, height: 22)
            row.viewWithTag(3)?.frame = CGRect(x: contentW - 80, y: 8, width: 80, height: 30)
        }

        if let row = sheet.viewWithTag(120) {
            row.frame = CGRect(x: 20, y: 96 + CGFloat(tierSwitches.count) * 46, width: contentW, height: 56)
            customSwitch.frame = CGRect(x: 0, y: 12, width: 51, height: 31)
            row.viewWithTag(2)?.frame = CGRect(x: 60, y: 16, width: 60, height: 22)
            let fieldW: CGFloat = 60
            customMin.frame = CGRect(x: 122, y: 12, width: fieldW, height: 30)
            row.viewWithTag(3)?.frame = CGRect(x: 184, y: 12, width: 12, height: 30)
            customMax.frame = CGRect(x: 198, y: 12, width: fieldW, height: 30)
            customCount.frame = CGRect(x: contentW - 64, y: 12, width: 64, height: 30)
        }

        let monthY = 96 + CGFloat(tierSwitches.count) * 46 + 60
        sheet.viewWithTag(121)?.frame = CGRect(x: 20, y: monthY, width: 80, height: 30)
        monthButton.frame = CGRect(x: 100, y: monthY, width: contentW - 80, height: 30)

        let buttonsY = sheetH - bottom - 62
        sheet.viewWithTag(130)?.frame = CGRect(x: 20, y: buttonsY, width: 90, height: 40)
        sheet.viewWithTag(131)?.frame = CGRect(x: width - 120, y: buttonsY, width: 100, height: 40)
    }

    @objc private func close() {
        dismiss(animated: true)
    }

    @objc private func endEditing() {
        view.endEditing(true)
    }

    @objc private func chooseMonth() {
        let alert = UIAlertController(title: "指定月份", message: nil, preferredStyle: .actionSheet)
        for (index, option) in monthOptions.enumerated() {
            alert.addAction(UIAlertAction(title: option.label, style: .default) { [weak self] _ in
                self?.selectedMonth = index
                self?.updateMonthTitle()
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        if let pop = alert.popoverPresentationController {
            pop.sourceView = monthButton
            pop.sourceRect = monthButton.bounds
        }
        present(alert, animated: true)
    }

    @objc private func submit() {
        var specs: [ReceiptStore.Spec] = []
        var total = 0
        for i in 0..<tierSwitches.count {
            guard tierSwitches[i].isOn else { continue }
            guard let text = tierCounts[i].text, let count = Int(text) else {
                return showProblem("\(ReceiptStore.tierNames[i]) 的条数填得不对")
            }
            guard count >= 0 else { return showProblem("\(ReceiptStore.tierNames[i]) 的条数填得不对") }
            if count == 0 { continue }
            let range = ReceiptStore.tierRanges[i]
            specs.append(ReceiptStore.Spec(name: ReceiptStore.tierNames[i],
                                           min: range.0, max: range.1, count: count))
            total += count
        }
        if customSwitch.isOn {
            guard let minText = customMin.text, let minValue = Double(minText), minValue > 0,
                  let maxText = customMax.text, let maxValue = Double(maxText), maxValue > 0 else {
                return showProblem("自定义的金额范围要填两个正数")
            }
            if maxValue < minValue { return showProblem("自定义的最高金额不能小于最低金额") }
            guard let countText = customCount.text, let count = Int(countText), count >= 0 else {
                return showProblem("自定义的条数填得不对")
            }
            if count > 0 {
                specs.append(ReceiptStore.Spec(name: "自定义", min: minValue, max: maxValue, count: count))
                total += count
            }
        }
        if total == 0 { return showProblem("请先勾选档位并填上要生成的条数") }
        if total > ReceiptStore.maxBatch {
            return showProblem("一次性只能批量添加\(ReceiptStore.maxBatch)个记录")
        }
        guard monthOptions.indices.contains(selectedMonth) else { return }
        let month = monthOptions[selectedMonth]
        let made = ReceiptStore.shared.add(specs, year: month.year, month: month.month)
        dismiss(animated: true) { [weak self] in
            self?.onDone()
            let alert = UIAlertController(title: nil, message: "已添加 \(made) 条收款记录",
                                          preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
            UpdatePrompt.topViewController()?.present(alert, animated: true)
        }
    }

    private func showProblem(_ message: String) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
        present(alert, animated: true)
    }

    private func bound(_ value: Double) -> String {
        if abs(value - value.rounded()) < 0.001 { return String(Int(value.rounded())) }
        var text = ReceiptStore.money(value)
        while text.hasSuffix("0") { text.removeLast() }
        if text.hasSuffix(".") { text.removeLast() }
        return text
    }
}

extension ReceiptBatchSheet: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }
}
