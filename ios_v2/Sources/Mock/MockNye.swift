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
        case .liaotian:
            return MockChat.page(state: state, metrics: metrics, width: width,
                                 showsTopTitle: showsTopTitle)
        case .huafei:
            return MockHuafei.page(state: state, metrics: metrics, width: width,
                                   showsTopTitle: showsTopTitle)
        case .nongxin:
            return MockNongxin.page(state: state, metrics: metrics, width: width,
                                    showsTopTitle: showsTopTitle)
        case .qq:
            return MockQQ.page(state: state, metrics: metrics, width: width,
                               showsTopTitle: showsTopTitle)
        case .jd:
            return MockJD.page(state: state, metrics: metrics, width: width,
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
             .duanxin, .huidan, .liushui, .liaotian, .huafei,
             .nongxin, .qq, .jd:
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
        case 3:
            alipayIncoming(b, state, showsTopTitle: showsTopTitle)
        case 4:
            ledger(b, state, showsTopTitle: showsTopTitle)
        default:
            query(b, state, out: state.sub == 1, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    // MARK: - 明细转出1：明细（`buildStatement()`）

    private static func statement(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细", line: true) }

        // 整页几何按**参考图**（1280×2816，去掉 90px 状态栏后 × 1080/1280）逐块量出来，
        // 不再沿用安卓 DOM 那份（它把标题画在左上角、按钮文字顶在按钮上沿）。
        // 成品页的标题栏由 `MockDetailScreen` 统一画（headH = 156），所以这里 b.y = 0
        // 对应的屏幕位置是标题栏下沿；编辑页预览则先用 titleBar() 画 156 的标题栏，坐标一致。
        //
        //   收款人中心 262   金额中心 387   灰隔条 536…566   状态行中心 629
        //   明细首行中心 776 行距 84        第二组前隔断 86
        //   链接条 1487…1607  按钮 1695…1823  底部灰到 2388
        // 减掉 156 的标题栏后就是下面这些常量。
        b.centerBlock("收款人", s.value("收款人", "转给李强"),
                      size: dp(16), color: MockInk.grey,
                      padTop: dp(25.7), padBottom: 0)
        b.centerBlock("金额", s.value("金额", "-100.00"),
                      size: dp(30), color: MockInk.amount,
                      padTop: dp(14.1), padBottom: dp(31.7))
        b.fill(dp(10), MockInk.board)

        b.statusRow("交易成功", trailing: "进度查询  ›",
                    height: dp(42), bg: MockInk.white,
                    size: dp(15), check: dp(15),
                    padH: dp(13), gap: dp(10), trailingInset: dp(18))
        b.gap(dp(14))

        // 行距按参考图：15sp 字 + 上下各 5dp（行距 84px）
        let stmtSize = dp(15)
        let stmtPadV = dp(5)
        let stmtPadH = dp(13.3)

        b.row("收款账号", s.value("收款账号", "622143551800063640"),
              size: stmtSize, padV: stmtPadV, padH: stmtPadH)
        b.row("收款银行", s.value("收款银行", "中国农业银行"),
              size: stmtSize, padV: stmtPadV, padH: stmtPadH)
        // 两组之间的分断线：上面留 14.7dp、下面留 13dp（线心落在两行正中，合计 86px）
        b.divider(stmtPadH, padV: dp(14.7), after: dp(13))
        b.row("付款户名", s.value("付款户名", "李东"),
              size: stmtSize, padV: stmtPadV, padH: stmtPadH)
        b.row("付款账号", s.value("付款账号", "6221****3646"),
              size: stmtSize, padV: stmtPadV, padH: stmtPadH)
        b.row("转账方式", s.value("转账方式", "实时转账"),
              size: stmtSize, padV: stmtPadV, padH: stmtPadH)
        b.row("转账附言", s.value("转账附言", "--"),
              size: stmtSize, padV: stmtPadV, padH: stmtPadH)
        b.row("交易时间", s.value("交易时间", s.time),
              size: stmtSize, padV: stmtPadV, padH: stmtPadH)

        // 行下方灰隔条 → 白底链接条 → 整块灰底（按钮就落在这块灰上）
        b.gap(dp(16.3))
        b.fill(dp(10), MockInk.board)
        b.linkBlock("查看和TA的转账记录", trailing: "›",
                    bg: MockInk.white, padV: dp(11), size: dp(15), padH: stmtPadH)
        b.bottomFill(MockInk.board,
                     canvasHeight: showsTopTitle ? 2388 : 2388 - MockDetailScreen.headH)
        b.twoButtons("再转一笔", "通知收款人",
                     padV: dp(29.3), h: dp(42.7), padH: dp(21.7),
                     gap: dp(14.3), radius: dp(5), size: dp(16))
    }

    // MARK: - 支付宝转入：收支详情（参考图 1280×2816 逐块量）

    /// 这一页比其它子页长得多：类型 / 金额 / 余额 + 7 行明细 +「收起」
    /// + 计入收支开关 + 分类 / 归属账本 + 备注。
    ///
    /// 数值都按参考图（去掉 90px 状态栏后 ×1080/1280）量，再减掉 156 的成品页标题栏：
    ///   类型中心 151  金额中心 243  余额中心 352  首行中心 522（行距 86）
    ///   收起 1203  灰隔条 1272…1300  开关行 1300…1430  分类 1430…1562
    ///   归属账本 1562…1698  备注 1723…1947  底部浅灰 1947…2232
    private static func alipayIncoming(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("收支详情") }
        // 编辑页预览的页内先画了 156 的标题栏；成品页的标题栏在屏上、页面坐标从 0 起。
        // 页面内部都用相对坐标，只有「备注块终点」是绝对值，需要补上这段偏移。
        let header: CGFloat = showsTopTitle ? b.metrics.titleBarH : 0
        let board = Draw.color(0xfff7f7f7)

        b.kindBadgeRow("类型", text: s.value("类型", "代付"),
                       size: dp(17), icon: dp(13.3), gap: dp(4), padTop: dp(39.1))
        b.centerBlock("金额", s.value("金额", "+264.00"),
                      size: dp(32), color: MockInk.amount, bold: true,
                      padTop: dp(2.4), padBottom: 0)
        b.centerBlock("余额", s.value("余额", "余额 ¥ 289.25"),
                      size: dp(16), color: MockInk.label,
                      padTop: dp(7.5), padBottom: 0)
        b.gap(dp(32.7))

        let size = dp(15), padV = dp(5.3), padH = dp(15)
        b.pairRow("交易时间", s.value("交易时间", s.time), size: size, padH: padH, padV: padV)
        b.pairRow("付款方", s.value("付款方", "支付宝(中国)网络技术\n有限公司"),
                  size: size, padH: padH, padV: padV)
        b.pairRow("付款账户", s.value("付款账户", "1058****2456"),
                  size: size, padH: padH, padV: padV)
        b.pairRow("交易摘要", s.value("交易摘要", "代付"), size: size, padH: padH, padV: padV)
        b.pairRow("交易附言", s.value("交易附言", "李哈哈支付宝转账"),
                  size: size, padH: padH, padV: padV)
        b.pairRow("交易卡号", s.value("交易卡号", "6228****9073"),
                  size: size, padH: padH, padV: padV)
        b.pairRow("交易类型", s.value("交易类型", "转账"), size: size, padH: padH, padV: padV)

        b.gap(dp(6.5))
        b.collapsibleLink("收起", size: dp(16), color: MockInk.gold, padV: dp(6.7))

        b.gap(dp(6.4))
        b.fill(dp(9.3), board)
        b.toggleRow("计入收支", on: true, size: dp(16), height: dp(43.6), padH: padH)
        b.settingRow("分类", value: s.value("分类", "其他收入"), valueColor: MockInk.value,
                     size: dp(16), height: dp(43.9), padH: padH, chevron: true)
        b.settingRow("归属账本", value: s.value("归属账本", "请选择"),
                     valueColor: Draw.color(0xffbfbfbf),
                     size: dp(16), height: dp(45.2), padH: padH,
                     chevron: true, divider: false)
        b.fill(dp(8.5), board)

        b.noteBlock("备注", placeholder: "记录点什么……",
                    titleSize: dp(17), placeholderSize: dp(15),
                    padH: padH, padTop: dp(7.7), phGap: dp(9.5),
                    underlineGap: dp(7.6), bottom: header + dp(649))
        b.bottomFill(board, canvasHeight: showsTopTitle ? 2388
                      : 2388 - MockDetailScreen.headH)
    }

    // MARK: - 明细转出2 / 明细转入：明细查询（`buildQuery()`）

    private static func query(_ b: MockBuilder, _ s: MockState, out: Bool,
                              showsTopTitle: Bool) {
        let m = b.metrics
        if showsTopTitle { b.titleBar("明细查询") }
        // 明细查询内容下方露 #eeeeee 底板（安卓实测 nye_sub2_detail）
        b.autoBoard = MockInk.board

        b.cardRow("卡号", logo: "mock_abc",
                  text: s.value("卡号", out ? "6228 **** 0979" : "6221 **** 3665"))
        b.amountRow("金额", label: "交易金额",
                    value: s.value("金额", (out ? "-" : "+") + "100.00"))
        b.bandBlock()

        // 行距按安卓实测（nye_sub2_detail）：13sp 字 + 上下 9.4dp，行距 103px。
        // 注意 103 是相邻文字带的 top→top；measure_page.py 打印的「间距 69」是
        // 墨迹空隙（= 103 − 字带高 34），拿 69 当行距会让每行少 32px。
        let rowSize = dp(13)
        let rowPadV = dp(9.4)
        b.row("交易时间", s.value("交易时间", s.time), size: rowSize, padV: rowPadV)
        b.row("交易摘要", s.value("交易摘要", out ? "转支" : "转存"), size: rowSize, padV: rowPadV)
        if out {
            b.row("收款方", s.value("收款方", "李东"), size: rowSize, padV: rowPadV)
            b.row("收款账户", s.value("收款账户", "6221****3640"), size: rowSize, padV: rowPadV)
            b.row("收款银行", s.value("收款银行", "邮储银行"), size: rowSize, padV: rowPadV)
        } else {
            b.row("付款方", s.value("付款方", "邓赞梅"), size: rowSize, padV: rowPadV)
            b.row("付款账户", s.value("付款账户", "6221****3640"), size: rowSize, padV: rowPadV)
            b.row("付款银行", s.value("付款银行", "邮储银行"), size: rowSize, padV: rowPadV)
        }
        b.row("交易类型", s.value("交易类型", "转账"), size: rowSize, padV: rowPadV)
        b.row("交易渠道", s.value("交易渠道", "超级网银"), size: rowSize, padV: rowPadV)
        b.row("本次余额", s.value("本次余额", "100.00"), size: rowSize, padV: rowPadV)
        b.row("交易附言", s.value("交易附言", "---"), size: rowSize, padV: rowPadV)
    }

    // MARK: - 收支详情（`buildLedger()`）

    private static func ledger(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细") }

        // 安卓 00_03 / 00_04：标题 16、金额 31、余额 16、明细行 16sp + 4.9dp（87px）。
        // 没有「类型/消费」那一行，也没有「查看更多」。
        let incoming = s.sub == 3
        b.centerBlock("金额", s.value("金额", incoming ? "+264.00" : "-264.00"),
                      size: dp(31), color: MockInk.amount, bold: true,
                      padTop: dp(10), padBottom: dp(4))
        b.centerBlock("余额", s.value("余额", "余额 ¥ 289.25"),
                      size: dp(16), color: MockInk.label,
                      padTop: 0, padBottom: dp(16))

        let size = dp(16)
        let padV = dp(4.9)
        b.row("交易时间", s.value("交易时间", s.time), size: size, padV: padV)
        if incoming {
            b.row("付款方", s.value("付款方", "支付宝(中国)网络技术有限公司"), size: size, padV: padV)
            b.row("收款账户", s.value("收款账户", "1058****2456"), size: size, padV: padV)
            b.row("交易摘要", s.value("交易摘要", "消费"), size: size, padV: padV)
            b.row("交易附言", s.value("交易附言", "商户支付宝转账"), size: size, padV: padV)
            b.row("交易卡号", s.value("交易卡号", "6228****9073"), size: size, padV: padV)
            b.row("交易类型", s.value("交易类型", "转账"), size: size, padV: padV)
        } else {
            b.row("收款方", s.value("收款方", "海州区好客士便利店"), size: size, padV: padV)
            b.row("收款账户", s.value("收款账户", "1050****5157"), size: size, padV: padV)
            b.row("交易摘要", s.value("交易摘要", "代付"), size: size, padV: padV)
            b.row("交易附言", s.value("交易附言", "TH404022415986431658"), size: size, padV: padV)
        }
    }
}
