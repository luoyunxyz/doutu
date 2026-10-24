import UIKit

/// 预览版式的总入口 —— 对应 Android 各 `XxxActivity.buildPreview()` 里的
/// `if (sub == 0) … else if (sub == 4) … else …` 分派。
///
/// 每加一家银行就在 `page(...)` 里加一个分支、再写一个 `MockXxx` 文件；
/// 编辑页与成品页共用同一份版式，只换 `MockMetrics`。
enum MockComposer {

    /// 生成一屏预览
    /// - Parameters:
    ///   - showsTopTitle: 编辑页的预览里有「‹ 明细」那条标题栏，成品页由页面自己画 header，所以不重复画
    static func page(module: MockModule,
                     state: MockState,
                     metrics: MockMetrics,
                     width: CGFloat,
                     showsTopTitle: Bool) -> MockPage {
        switch module {
        case .nye:
            return MockNye.page(state: state, metrics: metrics, width: width,
                                showsTopTitle: showsTopTitle)
        case .icbc:
            return MockIcbc.page(state: state, metrics: metrics, width: width,
                                 showsTopTitle: showsTopTitle)
        case .boc:
            return MockBoc.page(state: state, metrics: metrics, width: width,
                                showsTopTitle: showsTopTitle)
        case .psbc:
            return MockPsbc.page(state: state, metrics: metrics, width: width,
                                 showsTopTitle: showsTopTitle)
        case .cmb:
            return MockCmb.page(state: state, metrics: metrics, width: width,
                                showsTopTitle: showsTopTitle)
        case .eank:
            return MockEank.page(state: state, metrics: metrics, width: width,
                                 showsTopTitle: showsTopTitle)
        case .xingye:
            return MockXingye.page(state: state, metrics: metrics, width: width,
                                   showsTopTitle: showsTopTitle)
        case .jianshe:
            return MockJianshe.page(state: state, metrics: metrics, width: width,
                                    showsTopTitle: showsTopTitle)
        case .pingan:
            return MockPingan.page(state: state, metrics: metrics, width: width,
                                   showsTopTitle: showsTopTitle)
        case .jiaotong:
            return MockJiaotong.page(state: state, metrics: metrics, width: width,
                                     showsTopTitle: showsTopTitle)
        case .zhongxin:
            return MockZhongxin.page(state: state, metrics: metrics, width: width,
                                     showsTopTitle: showsTopTitle)
        case .pufa:
            return MockPufa.page(state: state, metrics: metrics, width: width,
                                 showsTopTitle: showsTopTitle)
        case .minsheng:
            return MockMinsheng.page(state: state, metrics: metrics, width: width,
                                     showsTopTitle: showsTopTitle)
        case .wechat:
            return MockWechat.page(state: state, metrics: metrics, width: width,
                                   showsTopTitle: showsTopTitle)
        case .alipay:
            return MockAlipay.page(state: state, metrics: metrics, width: width,
                                   showsTopTitle: showsTopTitle)
        case .huiwang:
            return MockHuiwang.page(state: state, metrics: metrics, width: width,
                                    showsTopTitle: showsTopTitle)
        case .shuzi:
            return MockDigitalWallet.page(state: state, metrics: metrics, width: width,
                                          showsTopTitle: showsTopTitle)
        case .yunshanfu:
            return MockUnionPay.page(state: state, metrics: metrics, width: width,
                                     showsTopTitle: showsTopTitle)
        case .cbi:
            return MockCbi.page(state: state, metrics: metrics, width: width,
                                showsTopTitle: showsTopTitle)
        case .tp:
            return MockTPWallet.page(state: state, metrics: metrics, width: width,
                                     showsTopTitle: showsTopTitle)
        case .okx:
            return MockOkx.page(state: state, metrics: metrics, width: width,
                                showsTopTitle: showsTopTitle)
        case .douyin:
            return MockDouyin.page(state: state, metrics: metrics, width: width,
                                   showsTopTitle: showsTopTitle)
        case .yindu:
            return MockYindu.page(state: state, metrics: metrics, width: width,
                                  showsTopTitle: showsTopTitle)
        case .duanxin:
            return MockSms.page(state: state, metrics: metrics, width: width,
                                showsTopTitle: showsTopTitle)
        case .huidan:
            return MockReceipt.page(state: state, metrics: metrics, width: width,
                                    showsTopTitle: showsTopTitle)
        case .liushui:
            return MockLiushui.page(state: state, metrics: metrics, width: width,
                                    showsTopTitle: showsTopTitle)
        default:
            return placeholder(module: module, metrics: metrics, width: width)
        }
    }

    /// 这家银行的版式有没有写（`isBuilt` 用它，避免两处清单走偏）
    static func hasLayout(_ module: MockModule) -> Bool {
        switch module {
        case .nye, .icbc, .boc, .psbc, .cmb, .eank,
             .xingye, .jianshe, .pingan, .jiaotong, .zhongxin, .pufa,
             .minsheng, .wechat, .alipay, .huiwang, .shuzi,
             .yunshanfu, .cbi, .tp, .okx, .douyin, .yindu,
             .duanxin, .huidan, .liushui:
            return true
        }
    }

    /// 尚未移植版式的模块：给一页明确的占位，而不是空白
    static func placeholder(module: MockModule, metrics: MockMetrics, width: CGFloat) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        b.titleBar(module.subs.first ?? module.title)
        b.gap(dp(40))
        b.centerBlock(nil, module.title, size: dp(17), color: MockInk.title,
                      bold: true, padTop: 0, padBottom: dp(12))
        b.centerBlock(nil, "版式制作中", size: dp(14), color: MockInk.label,
                      padTop: 0, padBottom: dp(8))
        b.centerBlock(nil, "子页：\(module.subs.count) 个", size: dp(13), color: MockInk.label,
                      padTop: 0, padBottom: dp(40))
        return b.result
    }
}

/// 「模拟 → 农业模拟」的五个子页版式。
///
/// 逐块对应 `NyeActivity.buildStatement()` / `buildQuery()` / `buildLedger()`
/// 与 `NyeDetailActivity.buildStatement()` / `buildQuery()` / `buildLedger()`
/// （两者只差字号与内边距，差异收在 `MockMetrics` 里）。
enum MockNye {

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            statement(b, state, showsTopTitle: showsTopTitle)
        case 4:
            ledger(b, state, showsTopTitle: showsTopTitle)
        default:
            query(b, state, out: state.sub == 1, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    // MARK: - 明细转出1：明细（`buildStatement()`）

    private static func statement(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let m = b.metrics
        if showsTopTitle { b.titleBar("明细") }

        b.centerBlock("收款人", s.value("收款人", "转给李强"),
                      size: m.nameSize, color: MockInk.grey,
                      padTop: m.namePadTop, padBottom: 0)
        b.centerBlock("金额", s.value("金额", "-100.00"),
                      size: m.amountSize, color: MockInk.amount, bold: true,
                      padTop: m.amountPadTop, padBottom: m.amountPadBottom)
        b.gap(dp(6))

        b.statusBar("交易成功", trailing: "进度查询  ›")

        b.row("收款账号", s.value("收款账号", "622143551800063640"))
        b.row("收款银行", s.value("收款银行", "中国农业银行"))
        b.divider(m.rowPadH)
        b.row("付款户名", s.value("付款户名", "李东"))
        b.row("付款账号", s.value("付款账号", "6221****3646"))
        b.row("转账方式", s.value("转账方式", "实时转账"))
        b.row("转账附言", s.value("转账附言", "--"))
        b.row("交易时间", s.value("交易时间", s.time))

        b.gap(dp(12))
        b.linkBlock("查看和TA的转账记录", trailing: "›")
        b.twoButtons("再转一笔", "通知收款人")
    }

    // MARK: - 明细转出2 / 明细转入 / 支付宝转入：明细查询（`buildQuery()`）

    private static func query(_ b: MockBuilder, _ s: MockState, out: Bool,
                              showsTopTitle: Bool) {
        let m = b.metrics
        if showsTopTitle { b.titleBar("明细查询") }

        b.cardRow("卡号", logo: "mock_abc",
                  text: s.value("卡号", out ? "6228 **** 0979" : "6221 **** 3665"))
        b.amountRow("金额", label: "交易金额",
                    value: s.value("金额", (out ? "-" : "+") + "100.00"))
        b.bandBlock()

        b.row("交易时间", s.value("交易时间", s.time))
        b.row("交易摘要", s.value("交易摘要", out ? "转支" : "转存"))
        if out {
            b.row("收款方", s.value("收款方", "李东"))
            b.row("收款账户", s.value("收款账户", "6221****3640"))
            b.row("收款银行", s.value("收款银行", "邮储银行"))
        } else {
            b.row("付款方", s.value("付款方", "邓赞梅"))
            b.row("付款账户", s.value("付款账户", "6221****3640"))
            b.row("付款银行", s.value("付款银行", "邮储银行"))
        }
        b.row("交易类型", s.value("交易类型", "转账"))
        b.row("交易渠道", s.value("交易渠道", "超级网银"))
        b.row("本次余额", s.value("本次余额", "100.00"))
        b.row("交易附言", s.value("交易附言", "---"))
    }

    // MARK: - 明细转出3：收支详情（`buildLedger()`）

    private static func ledger(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let m = b.metrics
        if showsTopTitle { b.titleBar("收支详情") }

        b.kindRow("类型", asset: "mock_ye_consume", text: s.value("类型", "消费"))
        b.centerBlock("金额", s.value("金额", "-264.00"),
                      size: m.ledgerAmountSize, color: MockInk.amount, bold: true,
                      padTop: m.ledgerPadTop, padBottom: m.ledgerPadBottom)
        b.centerBlock("余额", s.value("余额", "余额 ¥ 289.25"),
                      size: m.balSize, color: MockInk.label,
                      padTop: 0, padBottom: m.balPadBottom)

        b.row("交易时间", s.value("交易时间", s.time))
        b.row("收款方", s.value("收款方", "海州区好客士便利店"))
        b.row("收款账户", s.value("收款账户", "1050****5157"))
        b.row("交易摘要", s.value("交易摘要", "代付"))
        b.row("交易附言", s.value("交易附言", "TH404022415986431658"))

        b.moreRow("查看更多")
    }
}
