# 开屏跳过（SplashSkip）

个人自用的 Android 开屏广告跳过工具。只使用系统 `AccessibilityService`，不需要 root、Shizuku、Xposed/LSPosed，不修改系统，也不申请网络权限。

- 当前版本：`0.2.9`（versionCode 19，2026-10-19）
- 包名：`com.splashskip.app`
- 最低系统：Android 8.0（API 26）
- 编译/目标系统：Android 16（API 36）
- 已实测设备：vivo X100S（V2359A，Android 16，1260×2800，560 dpi）
- 使用范围：个人自用；源码以 MIT License 开源
- 仓库地址：https://github.com/acccount-l/skip-splash-ads

> 说明：本 README 记录的是 0.2.9 的状态。`0.2.9` 已在 vivo X100S 上同包名覆盖安装并验证：菜鸟 `homesplash_close_fullscreen`（文字“跳过 3”）真实点击成功并进入首页；中国移动 `video_time_skip`、百度地图 `sdm_myoffer_splash_skip_area` 此前也已真实点击成功。B 站、知乎、QQ音乐、微博此前已验证真实点击。网易云音乐、有道词典、闲鱼等仍待对应广告再次出现后确认。全探测模式是诊断功能，默认关闭，只记录、不点击；已在真机上验证可记录日志且不产生点击统计。

## 作者

- **Author**：林锦涛
- **Co-Author**：deepseek-v4.1-flash 1017、赵泽昊、李同博

## 环境适配

- **主测设备**：vivo X100S（V2359A），Android 16 / API 36，OriginOS 6，1260×2800，560 dpi。
- **系统范围**：minSdk 26（Android 8.0），compileSdk/targetSdk 36。只使用标准 `AccessibilityService`，不需要 root、Shizuku、Xposed/LSPosed，不修改系统分区，不申请 `INTERNET` 权限。
- **事件驱动**：只在目标 App 进入前台后的有限时间窗口内扫描，不做全天轮询；目标待机功耗 ≤1%/天（24 小时实测仍待完成）。
- **vivo / OriginOS 适配**：需要手动开启无障碍服务，并在“电池 → 后台高耗电”“i管家 → 自启动管理”“多任务卡片锁定”“电池优化”中放行。系统可能在省电、清理或重启后自动关闭无障碍服务，App 不能绕过，需要时手动重开。
- **其他机型**：目前只完成 vivo X100S 的真机验证；非 vivo 冒烟测试、24 小时待机功耗和长期保活尚未完成。

## 已实测可跳过的应用

以下应用在 vivo X100S 上通过真实点击验证（不是 dry-run）：

| 应用 | 包名 | 验证结果 |
|---|---|---|
| 哔哩哔哩 | `tv.danmaku.bili` | 真实点击成功 |
| 知乎 | `com.zhihu.android` | 真实点击成功 |
| QQ音乐 | `com.tencent.qqmusic` | 真实点击成功 |
| 微博 | `com.sina.weibo` | 真实点击成功（`tapByGesture`） |
| 中国移动 | `com.greenpoint.android.mc10086.activity` | 真实点击成功（`video_time_skip`） |
| 百度地图 | `com.baidu.BaiduMap` | 真实点击成功（`sdm_myoffer_splash_skip_area`） |
| 菜鸟 | `com.cainiao.wireless` | 真实点击成功（`homesplash_close_fullscreen`，进入 `HomePageActivity`） |

已内置规则、但尚未完成真实点击验证（默认 dry-run 或等待广告再次出现）的应用：淘宝、京东、拼多多、抖音、小红书、百度、高德地图、美团、携程、小黑盒、闲鱼、网易云音乐、网易有道词典。规则明细见第 2 节。

注意：开屏广告会随目标 App 版本、地区和广告 SDK 变化；规则命中不代表永久有效，失效时应重新抓取节点树后收紧规则。

## 1. 功能

- 标准倒计时开屏广告：识别“跳过/关闭/Skip”语义控件并点击。
- 有“跳过/关闭”控件的摇一摇广告页：尽快关闭，缩短摇动触发窗口。
- 事件驱动：只在目标 App 进入前台后的有限时间窗口内扫描，不做全天轮询。
- 全局 dry-run：默认开启，只记录“本应点击”，绝不点击。
- 规则级 dry-run：规则文件里写 `"dryRun": true` 时，即使全局关闭 dry-run，该规则也只记录。
- 每个 App、每条规则都可以在“规则”页单独启用/停用。
- 首页提供“打开无障碍开关”按钮，跳到系统无障碍页并尽量定位到本服务。
- 持久化脱敏日志，进程被杀后仍能查看；支持复制和清空。
- 今日命中/实际跳过统计、最近命中时间。
- 同包重复启动：从桌面再次启动同一个 App 时，会作为新会话重新检测。
- 全探测模式（0.2.0 新增，默认关闭）：不依赖内置规则，在任意 App 进入前台后的 10 秒内只读扫描，把“跳过/关闭广告”类候选写入本机日志；绝不点击、返回或执行手势。
- 探测模式与规则模式互斥：开启时规则开关和 dry-run 都不参与，关闭后恢复原有按包名/Activity 的规则跳过。

### 全探测模式（0.2.0 新增，默认关闭）

开关位置：首页“安全模式（dry-run）”下方的“全探测模式（只记录，不点击）”。

- **触发方式**：订阅所有应用的前台窗口事件；检测到包切换后开始 10 秒会话。同一 App 内部的 Activity 切换不会重启 10 秒窗口。Android 没有可靠的“冷启动”标准 API，因此这是近似实现，不能保证只在冷启动时触发。
- **只记录**：只匹配短文本/内容描述中的“跳过 / Skip / 关闭广告 / Close ad”等语义，日志以 `[探测]` 开头，记录包名、窗口类名、候选文字、viewId、class、bounds、归一化中心百分比、可点击祖先。其他页面文字不写入。
- **绝不动作**：探测路径不调用 `ACTION_CLICK`、`dispatchGesture` 或 `GLOBAL_ACTION_BACK`；不增加“命中/实际跳过”统计。即使关闭全局 dry-run，探测模式仍不会点击。
- **忽略的系统界面**：自身包、桌面、`android`、SystemUI、权限控制器、Google Play services、包安装器、已启用输入法。SystemUI/输入法等悬浮层不会结束当前探测会话；回到桌面会结束会话。
- **日志上限**：每轮最多 4 个候选、每个会话最多 10 个、每次会话最多 20 次扫描；跨会话按“包名|Activity|kind|来源|归一化文字|class|bounds”去重，最多记住 200 个键。日志文件仍受 128 KB × 2 轮转保护。
- **建议**：排查完成后关闭，以减少全包事件订阅和日志量。探测日志只是“候选”，不代表可以直接点击；之后补充规则仍必须通过包名/Activity/允许区域/deny 关键词/安全门。
- **已知限制**：只识别文本/描述，不识别纯 viewId 或纯图片按钮；`FLAG_SECURE`/敏感窗口可能读不到；单独“关闭”是较弱候选（`kind=close`），需要按包名和位置人工判断；不能自动生成规则；不建议长期开启。

## 2. 当前规则状态

内置 20 个规则文件、33 条规则，全部位于 `app/src/main/assets/rules/`。

| App | 包名 | 规则 | 状态 |
|---|---|---|---|
| 哔哩哔哩 | `tv.danmaku.bili` | `bilibili_splash_skip` | **真实点击已实测**；右下角；同包重复启动已修复 |
| 知乎 | `com.zhihu.android` | `zhihu_splash_probe` | **真实点击已实测**；右上角；要求倒计时数字；0.1.5 加入 `LaunchAdActivity` |
| 淘宝 | `com.taobao.taobao` | `taobao_splash_probe` | dry-run 探测；未真实点击 |
| 京东 | `com.jingdong.app.mall` | `jd_splash_probe` | dry-run 探测；未真实点击 |
| 拼多多 | `com.xunmeng.pinduoduo` | `pdd_splash_probe` | dry-run 探测；已实测命中 `跳过 3`，未真实点击 |
| 抖音 | `com.ss.android.ugc.aweme` | `douyin_splash_probe` | dry-run 探测；未真实点击 |
| 小红书 | `com.xingin.xhs` | `xhs_splash_probe` | dry-run 探测；未真实点击 |
| 微博 | `com.sina.weibo` | `weibo_splash_probe`、`weibo_splash_id_probe`、`weibo_splash_desc_probe` | **真实点击已实测**；0.1.8 文本规则使用 `tapByGesture`（节点树无可点击目标），区域收紧到右上角 `[0.5,0,1,0.2]`；ID/desc 探测仍 dry-run |
| 百度 | `com.baidu.searchbox` | `baidu_splash_probe` | dry-run 探测；未真实点击 |
| 高德地图 | `com.autonavi.minimap` | `amap_splash_probe` | dry-run 探测；未真实点击 |
| 美团 | `com.sankuai.meituan` | `meituan_splash_probe` | dry-run 探测；未真实点击 |
| 携程旅行 | `ctrip.android.view` | `ctrip_splash_probe` | dry-run 探测；曾出现广告但未识别，待继续验证 |
| 小黑盒 | `com.max.xiaoheihe` | `xiaoheihe_splash_probe` | 0.1.2 新增 dry-run 探测；未真机验证 |
| 百度地图 | `com.baidu.BaiduMap` | `baidumap_splash_probe`、`baidumap_splash_id_probe`、`baidumap_splash_desc_probe` | **真实点击已实测成功**；0.2.6 命中 `sdm_myoffer_splash_skip_area`（`[925,0][1260,322]`）并关闭；三路规则保留 |
| 闲鱼 | `com.taobao.idlefish` | `xianyu_splash_probe`、`xianyu_splash_id_probe`、`xianyu_splash_desc_probe` | dry-run 已命中 `text="跳过广告 5"` bounds `[922,112][1204,196]`；0.1.8 文本规则改为真实点击并收紧右上角 `[0.4,0,1,0.2]`，白名单改为 `InitActivity` / `SplashAdDialog`；真实点击待广告再次出现 |
| 网易云音乐 | `com.netease.cloudmusic` | `netease_cloudmusic_splash_probe` | dry-run 已实测命中；0.1.6 改为真实点击并收紧右上区域，待真机验证 |
| QQ音乐 | `com.tencent.qqmusic` | `qqmusic_splash_probe` | **真实点击已实测成功**；0.1.5 开启 |
| 网易有道词典 | `com.youdao.dict` | `youdao_dict_splash_probe`、`youdao_dict_splash_id_probe` | 文本规则 dry-run 已实测命中；0.1.6 改为真实点击并收紧右上区域；ID 规则仍 dry-run |
| 中国移动 | `com.greenpoint.android.mc10086.activity` | `china_mobile_splash_id_probe`、`china_mobile_splash_text_probe`、`china_mobile_splash_desc_probe`、`china_mobile_splash_countdown_probe` | **真实点击已实测成功**；`video_time_skip` 在 `[995,121][1210,215]` 命中“2 跳过”，手势点击关闭 |
| 菜鸟 | `com.cainiao.wireless` | `cainiao_splash_id_gesture`、`cainiao_splash_id_click`、`cainiao_splash_text` | **真实点击已实测成功**；`AdsActivity` 的 `homesplash_close_fullscreen` 在 `[962,189][1197,288]` 命中“跳过 3”，手势点击后进入 `HomePageActivity` |

补充：

- 快手 `com.smile.gifmaker` 未安装在主用户，因此没有加入规则；B 站作为替代验证对象。
- 网易云音乐 `com.netease.cloudmusic` 在另一台设备上已实测命中 `MainActivity` 的 `跳过 5`，bounds `[1025,154][1204,252]`；0.1.6 将该规则改为真实点击，允许区域从 `[0.5,0,1,0.4]` 收紧到 `[0.7,0,1,0.2]`。
- QQ音乐 `com.tencent.qqmusic` 20.8.5.8 已安装在主用户，启动 Activity 为 `com.tencent.qqmusic.activity.AppStarterActivity`；0.1.4 已实测 dry-run 命中：`text="跳过" bounds=[1032,161][1207,249] dryRun=true`。0.1.5 将该规则改为 `dryRun: false`，后续真机验证真实点击成功。QQ音乐规则参考公开的 GKD 规则结构，未复制其代码。
- 网易有道词典 `com.youdao.dict` 已在另一台设备上实测命中 `DictSplashActivity` 的 `跳过 3`，bounds `[755,145][871,202]`；0.1.6 将文本规则改为真实点击，区域从全屏收紧到 `[0.5,0,1,0.2]`。`skip_bottom_view` viewId 规则未命中，继续保持 dry-run 和全屏探测。
- 微博 `com.sina.weibo` 开屏页的“跳过”是 TextView，`uiautomator dump` 和 App 自身扫描都确认整棵节点树没有 clickable 节点，`ACTION_CLICK` 无法点击。0.1.8 为该规则加入 `tapByGesture: true`，在已验证“跳过”节点中心发一次 60ms 单点手势。实测两次成功：`text="跳过"` bounds `[1008,208][1211,320]`、`[1036,222][1236,332]`，均进入 `MainTabActivity`，未打开广告落地页。
- 闲鱼 `com.taobao.idlefish` 0.1.8 dry-run 实测命中：`text="跳过广告 5"` bounds `[922,112][1204,196]`，且通过安全门（存在可点击目标）。开屏广告实际是 `SplashAdDialog` 窗口，0.1.8 将该窗口类名加入规则白名单、移除 `MainActivity`，文本规则改为真实点击。冷启动三次中只有第一次出现广告，后续受频控未再出现，真实点击仍需下一次广告验证。
- 百度地图 `com.baidu.BaiduMap` 0.1.8 冷启动三次，0.4 秒时已进入地图主页，未出现开屏广告；三路规则保持 dry-run。规则白名单同时包含 `WelcomeScreen.*` 和 `MapsActivity`，等广告出现后再确认控件。
- 菜鸟 `com.cainiao.wireless` 8.11.1008：冷启动依次经过 `WelcomeActivity` → `AdsActivity`；关闭控件为 `homesplash_close_fullscreen`（TextView，clickable），文字示例“跳过 3”，bounds `[962,189][1197,288]`。0.2.9 新增三条规则：viewId + 文本手势规则优先，纯 viewId 点击规则兜底，文本手势规则兜底未知 viewId；实测手势点击后进入 `HomePageActivity`。
- 京东、淘宝、抖音、高德曾冷启动多次但未出现开屏广告，规则尚未获得真实命中样本。
- 拼多多在其他机器上出现过开屏广告，但日志中没有“开始检测”等记录；需要先排查服务/事件过滤，而不是继续改匹配规则。
- 携程曾出现开屏广告但未被当前通用规则识别，需后续抓取该版本节点树再收紧。
- 0.1.2 起，淘宝、拼多多、小黑盒、百度地图的探测规则允许非 clickable 文本节点；安全门会向上寻找可点击父节点，并继续检查区域、deny 关键词和面积上限。
- 0.1.2 起，上述探测规则同时接受“跳过广告 / 点击跳过 / 跳过 3s / 跳过 5 秒”等常见文案变体；仍拒绝“跳过广告，立即下载”这类叠加营销词的文本。
- 0.1.2 起增加两类诊断日志：每个会话首次成功读取界面时，记录节点总数和最多 4 个“跳过类”候选的脱敏文本/class/bounds；当前 Activity 不在规则白名单时，记录一行 `Activity 不在规则白名单`。用于区分“没收到事件 / Activity 不匹配 / 节点不可读 / 区域不匹配”。
- dry-run 探测规则使用较宽的右侧区域，只记录候选，不会真实点击。确认命中正确后，才可以逐条收紧并关闭 dry-run。

## 3. 安全设计

目标不是“尽量多点”，而是“不确定就不点”。以下检查是硬编码的，规则只能缩小范围，不能关闭：

1. **前台包名必须等于规则包名**，节点包名也必须一致。
2. **Activity 白名单**：当前 Activity 必须在该规则的 `activityIds` 中。
3. **允许区域**：候选节点中心必须落在规则 `allowedBounds` 内；未配置时默认右上角 `[0.65, 0.0, 1.0, 0.25]`。
4. **拒绝关键词**：文本包含“下载/安装/更新/升级/购买/支付/付款/充值/开通/订阅/续费/授权/登录/注册/领取/提现/绑定/实名/同意/允许/打开/去看看/立即购买/免费领取/红包/优惠券”等直接拒绝；英文同类词也拒绝。
5. **拒绝 viewId 正则**：包含 `install/download/pay/buy/purchase/login/auth/update` 的控件直接拒绝。
6. **节点状态**：候选和实际点击目标都必须 `visibleToUser && enabled && clickable`。例外：显式开启 `tapByGesture` 的规则允许候选本身不可点击，但候选必须是可见、启用、文字/内容描述符合“跳过/关闭”语义的节点；点击前仍会重新校验快照、允许区域和面积。
7. **点击目标面积**：不能超过屏幕面积的 25%，防止误点整块广告容器。
8. **尝试次数 + 冷却**：每个开屏会话的尝试次数、冷却和时间窗口由规则配置（1–3 次、0–10000 ms、500–30000 ms）；当前多数规则为 3 次、100–2000 ms、8–10 秒。
9. **快照校验**：点击前重新按节点路径取节点，并校验 viewId、className、bounds；含 `text`/`texts`/`contentDescription` 的规则还要求文字/描述一致。纯 viewId/class 规则允许倒计时文字变化；重匹配失败时在当前界面重新扫描，仍失败则放弃本次点击。
10. **失败关闭**：任何一步失败都不点击，不回落坐标点击。
11. **dry-run**：全局开关默认开启；规则级 `dryRun: true` 是硬保险。
12. **全探测模式硬隔离**：`probeAll` 开启时，`performScan` / `handleHits` / `performClick` / `tapNodeCenter` 四处都有二次守卫；探测路径只调用 `ProbeMatcher` 并写日志，不调用点击、手势或返回，也不读写命中/点击统计。切换开关时先结束规则会话和探测会话，再重设事件过滤。

不支持、也不做：

- 不点击广告内容、下载、安装、支付、授权、登录等控件。
- 不做坐标盲点、OCR、截图、录屏。手势仅用于显式开启 `tapByGesture` 且通过安全门的文字节点，坐标取该节点实时 bounds 中心，不使用硬编码或推测坐标。
- 不阻止目标 App 接收加速度传感器事件；摇一摇广告只在出现“跳过/关闭”控件时处理。
- 不处理信息流、横幅、弹窗、贴片等应用内广告。
- 不破解第三方 App、不绕过付费墙。

## 4. 隐私与权限

- **没有 `INTERNET` 权限**：App 不能联网，规则不会在线更新，日志不会上传。
- **没有存储权限**：日志只写入应用私有目录。
- **没有悬浮窗、通知、前台服务、媒体投影、`QUERY_ALL_PACKAGES`**。
- Manifest 只有一个 `HOME` intent 的 `<queries>`，用于解析桌面包名，让“回到桌面”能结束当前检测窗口；这是包可见性声明，不是权限，也不读取桌面内容。
- `android:allowBackup="false"`，`android:usesCleartextTraffic="false"`。
- 没有 WebView、动态代码加载、hidden API、Shizuku/root、第三方分析 SDK。
- 日志中只保留：包名、Activity、规则 id、节点 class、命中文本、bounds、dry-run 状态、是否使用手势。文本仅在包含“跳过/关闭/Skip/Close”且严格符合短文案时保留，截断 20 字；诊断日志最多记录 4 个“跳过类”候选。其他文本写成 `<已隐藏 N 字>`，不记录完整节点文本或整棵节点树。
- **全探测模式的隐私边界**：开启后会短时间（约 10 秒）读取任意前台应用的控件文字与内容描述；只把命中“跳过/关闭广告”语义的短文本、位置和可点击祖先类名写入本机日志，不保存完整界面、不记录其他页面文字、不截图、不 OCR、不上传。该模式默认关闭，建议排查完成立即关闭。
- 探测日志字段：包名、Activity、`kind=skip/close`、`src=text/desc`、短文本、viewId、class、bounds、中心百分比、可点击祖先 class/bounds。不增加命中/点击统计。
- “复制”按钮会把当前日志放入系统剪贴板，这是用户主动操作；其他 App 是否能读剪贴板取决于系统版本和权限。

## 5. 日志策略（0.1.1 起）

**结论：有自动清理，但不是按天删除，而是按大小轮转、删除最旧的一份。正常不会无限堆积。**

| 项目 | 策略 |
|---|---|
| 内存 | 最多 200 条，App 重启后从磁盘恢复最近 200 条 |
| 当前文件 | `files/splash-log.txt`，达到 128 KB 轮转 |
| 备份文件 | `files/splash-log.1.txt`，保留上一份 |
| 磁盘总上限 | 约 256 KB（2 × 128 KB） |
| 轮转 | 当前文件满 128 KB → 删除旧备份 → 当前文件改名为备份 → 新文件继续写 |
| 启动清理 | 如果旧版本留下超大文件，启动时只保留末尾 128 KB；超大备份直接删除 |
| 异常兜底 | 改名失败时删除当前文件；删除也失败时直接清空，保证不会无限增长 |
| 手动清空 | 日志页“清空”同时删除内存和两个磁盘文件 |
| 时间自动删除 | 没有按天/按周删除；大小上限就是清理机制 |

正常使用下，磁盘占用上限约 256 KB，相当于一张小图片的零头，不会明显占用手机存储。需要保留更长的排查记录时，请先“复制”日志，再清空。

`0.2.0` 的全探测日志以 `[探测]` 开头，额外受以下上限约束：每轮最多 4 个候选、每个会话最多 10 个、每次会话最多 20 次扫描；跨会话去重最多记住 200 个键（数字归一化，避免“跳过 5/4/3”重复刷屏）。探测日志与普通日志共用同一个内存 200 条上限和 128 KB × 2 文件轮转。

调试命令（仅 debug 构建可用 `run-as`）：

```bash
adb shell run-as com.splashskip.app ls -l files/
adb shell run-as com.splashskip.app cat files/splash-log.txt
adb shell run-as com.splashskip.app cat files/splash-log.1.txt
```

日志同时输出到 logcat，tag 为 `SplashSkip`：

```bash
adb logcat -s SplashSkip
```

## 6. 项目结构

```text
SplashSkip/
├─ app/
│  ├─ src/main/
│  │  ├─ AndroidManifest.xml
│  │  ├─ assets/rules/*.json          # 内置规则，不联网更新
│  │  ├─ kotlin/com/splashskip/app/
│  │  │  ├─ data/                     # 规则加载、设置、日志存储
│  │  │  ├─ service/                  # 无障碍服务、节点映射
│  │  │  ├─ ui/                       # Compose 界面
│  │  │  └─ SplashSkipApp.kt
│  │  └─ res/xml/accessibility_service_config.xml
│  └─ src/test/                       # 规则资产解析、日志编码测试
├─ rule-engine/                       # 纯 Kotlin/JVM 规则引擎，可单测
│  ├─ src/main/kotlin/.../engine/
│  │  ├─ RuleMatcher.kt
│  │  ├─ SafetyGate.kt
│  │  ├─ ProbeMatcher.kt            # 全探测模式的只读候选匹配
│  │  ├─ Bounds.kt
│  │  └─ RuleParser.kt
│  └─ src/test/kotlin/...
├─ gradle/libs.versions.toml
├─ gradle/wrapper/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ gradlew / gradlew.bat
├─ README.md
└─ LICENSE
```

## 7. 构建

### 7.1 环境

- Android Studio（推荐）或 JDK 17+；本项目当前使用 `C:/Program Files/Java/jdk-24`。
- Android SDK Platform 36、Build-Tools 36.0.0。
- 仓库中的 `gradle.properties` 未写死 JDK 路径；如本机需要指定 JDK，可自行添加 `org.gradle.java.home`，或使用 `JAVA_HOME` / Android Studio 自带 JDK。
- Gradle Wrapper 8.14.3，AGP 8.13.2，Kotlin 2.2.21，Compose BOM 2025.10.01。
- 依赖版本固定在 `gradle/libs.versions.toml`，不使用动态版本。
- Wrapper 使用腾讯云镜像获取 Gradle 8.14.3；`distributionSha256Sum` 来自官方 `services.gradle.org`，不是第三方修改包。

仓库不包含 `local.properties`，需要在本机生成，例如：

```properties
sdk.dir=C:/Users/你的用户名/Android/Sdk
```

或者直接用 Android Studio 打开项目，让 IDE 生成。

### 7.2 命令

Windows：

```bat
gradlew.bat :rule-engine:test :app:testDebugUnitTest :app:assembleDebug
```

Git Bash / macOS / Linux：

```bash
./gradlew :rule-engine:test :app:testDebugUnitTest :app:assembleDebug
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

仓库不提交 APK、构建产物和本机配置；如需安装，请按上面的命令自行构建。

### 7.3 测试

- `rule-engine`：规则解析、匹配、边界、安全门、快照回放；`ProbeMatcherTest` 覆盖 16 个用例（跳过/关闭变体、拒绝危险文案、text/desc 优先级、不可见/禁用/越界节点、可点击祖先、中心归一化、上限与去重、NBSP 空白）。
- `app`：内置规则资产全部可解析、规则 id 唯一、日志行编码/解码；`NewRulesTest` 当前 20 个用例全部通过，其中菜鸟新增 6 个（手势命中、倒计时前缀、拒绝广告 CTA、纯 viewId 点击、越界拒绝、文本兜底）。
- 构建命令会同时运行单元测试；当前构建结果 `BUILD SUCCESSFUL`。

## 8. 安装与首次使用

1. 安装 debug APK：

   ```bash
   adb install -r SplashSkip-v0.2.9-debug.apk
   ```

   如果提示签名不一致，需要先卸载旧版再安装；卸载会清除设置和日志。`0.2.9` 与此前版本使用同一 applicationId 和同一 debug keystore 构建时，`-r` 会覆盖安装并保留设置/日志。

2. 打开“开屏跳过”，点“打开无障碍开关”，在系统列表中开启本服务。

3. Android 13+ 如果提示“受限设置”，到系统“应用信息 → 右上角 ⋮ → 允许受限设置”放行。不同 ROM 的入口名称可能不同。

4. 保持全局“安全模式（dry-run）”开启，冷启动 B 站/知乎，到“日志”页确认命中文本、Activity、bounds 是否正确。

5. 确认无误后，再关闭全局 dry-run 做真实点击测试。B 站和知乎规则本身是 `dryRun: false`，但全局 dry-run 仍会覆盖它们。

6. vivo / OriginOS 保活（只做引导，App 不自动修改系统设置）：

   - 设置 → 电池 → 后台高耗电：允许“开屏跳过”。
   - i管家 → 自启动管理：允许“开屏跳过”。
   - 多任务卡片下拉，锁定“开屏跳过”。
   - 电池优化设为“无限制”。
   - 注意：vivo 可能在省电/清理/重启后自动关闭无障碍服务，这是系统行为，App 不能绕过；需要时手动重新开启。

## 9. 规则格式

一个规则文件对应一个包名：

```json
{
  "schemaVersion": 1,
  "packageName": "com.example.app",
  "rules": [
    {
      "id": "example_splash_skip",
      "enabled": true,
      "activityIds": [
        "com.example.app.LauncherActivity"
      ],
      "match": {
        "textRegex": "^(跳过|Skip)\\s*\\d+$",
        "className": "android.widget.TextView",
        "clickable": true,
        "allowedBounds": [0.65, 0.0, 1.0, 0.25]
      },
      "action": "CLICK",
      "constraints": {
        "withinMsAfterLaunch": 8000,
        "cooldownMs": 2000,
        "maxAttempts": 1,
        "dryRun": true
      }
    }
  ]
}
```

字段说明：

- `schemaVersion`：当前只支持 `1`。
- `packageName`：目标 App 包名，必须与前台包名一致。
- `activityIds`：Activity 白名单，至少 1 个，最多 10 个。也可以填写实测确认的开屏广告窗口类名（例如闲鱼的 `com.taobao.fleamarket.splashad.SplashAdDialog`），不能凭猜测添加。
- `match.textRegex` / `texts`：二者是 OR 关系；`texts` 是精确匹配。`textRegex` 建议要求倒计时数字，避免匹配到普通页面上的“跳过”。
- `match.contentDescriptionRegex` / `viewIdRegex`：可选的辅助条件，所有已填写条件按 AND 组合。
- `match.className`：精确类名。
- `match.clickable`：是否要求候选自身可点击，默认 `true`。
- `match.allowedBounds`：归一化 `[left, top, right, bottom]`，取值 0..1；为空时默认右上角。
- `action`：`CLICK` 或 `BACK`。
- `tapByGesture`：默认 `false`。仅用于“有跳过文字、但整棵节点树没有 clickable 节点”的广告页；开启后规则必须至少包含 `textRegex`、`texts` 或 `contentDescriptionRegex` 之一，点击前仍要通过安全门、快照校验和面积/区域复检。
- `constraints.withinMsAfterLaunch`：进入 App 后的检测窗口，500–30000 ms。
- `constraints.cooldownMs`：两次尝试的冷却，0–10000 ms。
- `constraints.maxAttempts`：每个会话最多尝试次数，1–3。
- `constraints.dryRun`：`true` 时该规则永不真实点击。

规则只从 APK 内置 assets 读取；修改 JSON 后需要重新构建/安装，App 不联网更新规则。

## 10. 已知限制

- **摇一摇传感器无法阻断**：第三方 App 没有标准 API 拦截另一个 App 的加速度传感器；本 App 只能在广告页出现后尽快点“跳过/关闭”。没有跳过/关闭控件的摇一摇广告不处理。
- **自定义绘制/Flutter/Unity/WebView canvas/FLAG_SECURE** 可能没有可读节点；读不到安全控件就不点。
- **全探测模式只识别文本/描述**：纯 viewId、纯图片按钮、canvas 绘制无法发现；单独“关闭”是较弱候选（`kind=close`），需要按包名、位置和可点击祖先人工判断；不能自动生成规则，也不代表候选一定可以点击。
- **全探测模式会增加事件订阅与日志量**：仅在排查时临时开启，用完关闭；它不会点击，但长时间开启会收到更多窗口事件。全探测模式已在 vivo 真机上验证可记录候选、不增加点击统计；长期开启的事件量与耗电仍建议按需使用。
- **`accessibilityDataSensitive`**：Android 14+ 可能把某些视图标记为敏感；本 App 如实声明 `isAccessibilityTool="false"`，读不到就失败关闭，不伪装成残障辅助工具。
- **Android 17 Advanced Protection Mode**：开启 AAPM 后，非无障碍工具类服务可能被阻止/撤销；这是平台策略，不能也不应绕过。当前设备是 Android 16。
- **vivo/OriginOS 可能自动关闭无障碍服务**：App 不能自动改系统设置；需要用户按第 8 节保活。
- **规则会随目标 App 改版失效**：需要重新抓节点树、收紧规则；目前完成真实点击验证的是 B 站、知乎、QQ音乐、微博、百度地图、中国移动、菜鸟。微博和菜鸟依赖手势点击，若广告改版导致“跳过”文字不再出现在节点树中，会失败关闭；菜鸟另有纯 viewId 点击规则兜底。
- **拼多多/淘宝日志为空的问题尚未定位**：如果其他机器上连“服务已连接/开始检测”都没有，优先检查无障碍服务是否真正运行、拼多多/淘宝是否在应用分身/隐私空间、以及规则是否启用；不是改匹配规则能解决的。
- **小黑盒规则未真机验证**：小黑盒仍待开屏广告出现后确认 Activity、命中文本、bounds 并收紧。百度地图已在 0.2.6 真机验证：`sdm_myoffer_splash_skip_area` 在 `[925,0][1260,322]` 命中并真实点击成功。
- **非 vivo 冒烟测试、24 小时待机功耗、长期保活**：尚未完成。
- **日志上限优化**：0.1.2 已在真机安装并确认服务重连，但尚未长期验证轮转行为。
- **无 release 签名**：当前只有 debug APK，使用本机 debug keystore；没有生成或保存 release 签名密钥。
- **未上架、未做应用商店合规评估**：目标 App 的服务条款、广告主投诉、无障碍 API 政策等风险由使用者自行评估。

## 11. 变更记录

### 0.2.9（2026-10-19）

- 新增菜鸟 `com.cainiao.wireless` 开屏跳过：`WelcomeActivity` / `AdsActivity`，关闭控件 `homesplash_close_fullscreen`（TextView，clickable），实测文字“跳过 3”、bounds `[962,189][1197,288]`。
- 新增三条菜鸟规则：`cainiao_splash_id_gesture`（viewId + 文本，`tapByGesture`，优先）、`cainiao_splash_id_click`（纯 viewId 的 `ACTION_CLICK` 兜底）、`cainiao_splash_text`（无 viewId 的文本手势兜底）；允许区域统一为右上角 `[0.5,0,1,0.2]`。
- vivo X100S 冷启动实测：00:05:32 命中 `cainiao_splash_id_gesture`，文字“跳过 3”，手势点击成功，随后前台进入 `HomePageActivity`；用户目视确认广告已关闭。
- 新增 6 个菜鸟单元测试；`NewRulesTest` 20 个用例全部通过。
- 版本号提升到 `0.2.9`（versionCode 19）。

### 0.2.8（2026-10-18）

- 扫描调度整体提速：窗口状态变化后立即扫描（0 ms），并在 50/100/150/200 ms 增加早期复查点。
- `CONTENT_DEBOUNCE_MS` 80→50 ms，`notificationTimeout` 100→50 ms，缩短事件到扫描的等待。
- 中国移动规则冷却保持 100 ms，密集复查覆盖 0.8–3 秒的开屏广告出现窗口。
- 这些改动是全局的，其他已适配 App 的命中时间同步缩短；真实效果仍受广告 SDK 暴露节点的时机限制。
- 版本号提升到 `0.2.8`（versionCode 18）。

### 0.2.7（2026-10-18）

- 中国移动 `video_time_skip` 的 `ACTION_CLICK` 返回 true 但广告不关闭；`china_mobile_splash_id_probe` 增加 `textRegex` 并改为 `tapByGesture: true`，在节点中心发送真实触摸。
- 中国移动文本规则支持倒计时前缀（如“2 跳过”）；`cooldownMs` 降到 100 ms，`maxAttempts` 提到 3。
- 增加 100 ms 级复查点，缩短从检测开始到点击的间隔。
- 真机日志确认 `tapByGesture=true` 且广告关闭；`执行成功` 不再单独作为业务成功依据，需结合界面结果确认。
- 版本号提升到 `0.2.7`（versionCode 17）。

### 0.2.6（2026-10-18）

- 修复开屏会话被后台窗口类名覆盖的问题：会话保留开始时的 `startActivity`；`AppTabFragment` 等不在规则白名单的窗口事件不再让 `StartPageActivity` 会话停止扫描。
- 点击前若倒计时文字或广告树结构变化，按同一规则在当前界面重新匹配；纯 viewId 规则不再因倒计时文字变化判定快照失效。
- `ACTION_CLICK` 不生效时，仅对仍含“跳过/关闭”语义的节点改用节点中心手势；保留允许区域、面积、可见/启用等安全门校验。
- 扫描点增加到 12 个（200ms、800ms、1.2s、1.6s、2s、2.4s、3s、3.5s、4s、5s、6s、8s），缩短开屏广告的关闭延迟。
- 中国移动规则：`maxAttempts` 1→3、`cooldownMs` 2000→500；`video_time_skip` 在 `[995,121][1210,215]` 命中并真实点击成功。
- 百度地图 SDK viewId 规则 `sdm_myoffer_splash_skip_area` 在 `[925,0][1260,322]` 命中并真实点击成功。
- 版本号提升到 `0.2.6`（versionCode 16）。

### 0.2.1–0.2.5（2026-10-18）

- 百度地图：新增 SDK viewId 规则并开启真实点击；`sdm_myoffer_splash_skip_area` 实测成功。
- 中国移动：新增 `StartPageActivity` / `WelcomeActivity` 规则；修复 `AppTabFragment` 覆盖 Activity 导致会话中断的问题。
- 增加 3s/4s/5s/6s/8s 复查扫描点；补充命中日志中的 viewId、startActivity 与首次非空节点树诊断。
- 期间版本号依次为 0.2.1–0.2.5（versionCode 11–15）。

### 0.2.0（2026-10-18）

- 新增“全探测模式”（首页开关，默认关闭）：订阅所有应用，在应用进入前台后的 10 秒内只读扫描，不依赖内置规则。
- 探测模式只把“跳过/关闭广告”类短文本候选写入本机日志（`[探测]` 前缀），记录包名、Activity、kind/source、文字、viewId、class、bounds、中心百分比、可点击祖先；不记录其他页面文字、完整节点树、截图。
- 探测路径绝不点击、返回或执行手势；开启时规则和 dry-run 均不参与，不增加命中/点击统计。`performScan`、`handleHits`、`performClick`、`tapNodeCenter` 增加 `probeAll` 二次守卫。
- 运行时事件过滤切换为 `packageNames = null`（所有应用）；关闭探测后恢复目标包 + 桌面包过滤。忽略自身、桌面、`android`、SystemUI、权限控制器、GMS、包安装器、已启用输入法；SystemUI/IME 悬浮层不结束当前探测会话，回桌面结束。
- 探测上限：10 秒会话、20 次扫描、每轮 4 个候选、每会话 10 个候选；跨会话去重 200 个键（数字归一化）。
- 新增纯 Kotlin `ProbeMatcher` 与 16 个 JVM 单元测试；`UiTree` 增加 `findClickableAncestor`，安全门复用同一实现。
- 首页新增“全探测模式（只记录，不点击）”卡片和观察模式警告；规则页在探测开启时显示“规则和 dry-run 不参与”横幅；无障碍摘要/说明补充全探测模式披露。
- 版本号提升到 `0.2.0`（versionCode 10）；applicationId 保持 `com.splashskip.app`，可同包名覆盖 0.1.8。
- 尚未完成 vivo 真机验证：`packageNames=null` 全包事件、探测日志、无点击证明、开关切换、IME/SystemUI 排除。

### 0.1.8（2026-10-18）

- 修复弹窗窗口类名覆盖 Activity 的问题：`BMAlertDialog`、`SplashAdDialog` 等窗口不再把已识别的 Activity 覆盖掉；规则显式列入 `activityIds` 的窗口仍然接受。
- 新增规则级 `tapByGesture`（默认关闭）。当“跳过”是普通 TextView、整棵节点树没有 clickable 节点时，在已验证文字节点中心发送一次 60ms 单点手势；仍要求文字/描述匹配、允许区域、deny 关键词、节点可见/启用、面积不超过屏幕 25%，点击前重新校验快照与实时 bounds。
- 微博 `weibo_splash_probe` 开启 `tapByGesture` 与真实点击，允许区域收紧到 `[0.5,0,1,0.2]`。实测两次成功跳过，进入 `MainTabActivity`。
- 闲鱼规则白名单改为 `InitActivity` + `SplashAdDialog`（移除 `MainActivity`）；文本规则允许区域收紧到 `[0.4,0,1,0.2]` 并开启真实点击。dry-run 已命中 `跳过广告 5`，真实点击待广告再次出现。
- 百度地图三次冷启动未出现开屏广告，规则保持 dry-run。
- 无障碍服务配置改为 `canPerformGestures="true"`。
- 规则页显示“手势点击”标记。
- 版本号提升到 `0.1.8`（versionCode 9）。

### 0.1.7（2026-10-18）

- 新增闲鱼 `com.taobao.idlefish` 的 dry-run 探测：文本、viewId、contentDescription 三路规则，覆盖 `InitActivity` / `MainActivity`。
- 微博规则扩展 Activity 白名单：`FlashAdActivity`、`mobileads.view.b`、`WeiboAdBrowser`，并增加 viewId、contentDescription 探测。
- 百度地图规则增加 viewId、contentDescription 探测；原有文本规则保留。
- 三条 App 的规则全部保持 dry-run，先获取真机命中样本，不直接点击。
- 规则页补充“闲鱼”中文名称。
- 版本号提升到 `0.1.7`（versionCode 8）。

### 0.1.6（2026-10-18）

- 网易云音乐 `netease_cloudmusic_splash_probe` 从 dry-run 改为真实点击，允许区域收紧到右上角 `[0.7,0,1,0.2]`。
- 有道词典文本规则 `youdao_dict_splash_probe` 从 dry-run 改为真实点击，允许区域收紧到右上角 `[0.5,0,1,0.2]`。
- 有道词典 `youdao_dict_splash_id_probe` 未命中，继续保持 dry-run 全屏探测。
- 版本号提升到 `0.1.6`（versionCode 7）。
- `0.1.6` 已安装到 vivo X100S；本机未安装网易云音乐和有道词典，两者的真实点击待另一台设备验证。

### 0.1.5（2026-10-18）

- QQ音乐 `qqmusic_splash_probe` 从 dry-run 改为真实点击；匹配条件、区域（右侧 `[0.5,0,1,0.4]`）和 10 秒窗口不变。
- 知乎规则加入 `com.zhihu.android.app.ui.activity.LaunchAdActivity`，让开屏广告页更早进入检测。
- 版本号提升到 `0.1.5`（versionCode 6）。

### 0.1.4（2026-10-18）

- 新增网易有道词典 `com.youdao.dict` 的 dry-run 探测：文本“跳过”规则 + `skip_bottom_view` viewId 规则，Activity 为 `DictSplashActivity` / `DictHotBootSplashActivity`。
- 有道词典文本规则暂用全屏区域，仅用于探测；确认实际控件后再收紧。
- 规则页补充“网易有道词典”中文名称。
- 版本号提升到 `0.1.4`（versionCode 5）。

### 0.1.3（2026-10-18）

- 新增网易云音乐 `com.netease.cloudmusic` 的 dry-run 探测规则，Activity 为 `LoadingActivity` / `MainActivity`，右侧区域，10 秒窗口。
- 新增 QQ音乐 `com.tencent.qqmusic` 的 dry-run 探测规则，Activity 为 `AppStarterActivity`，右侧区域，10 秒窗口。
- 网易云音乐规则依据公开的 GKD 规则信息，未复制其代码；当前设备未安装网易云音乐，无法真机验证。
- 规则页补充网易云音乐、QQ音乐中文名称。
- 版本号提升到 `0.1.3`（versionCode 4）。

### 0.1.2（2026-10-18）

- 新增小黑盒 `com.max.xiaoheihe`、百度地图 `com.baidu.BaiduMap` 的 dry-run 探测规则。
- 淘宝、拼多多、小黑盒、百度地图探测规则允许非 clickable 文本节点，由安全门向上寻找可点击父节点。
- 上述探测规则支持“跳过广告 / 点击跳过 / 跳过 3s / 跳过 5 秒”等文案变体，仍拒绝叠加营销词的文本。
- “开始检测”日志增加“当前 Activity 匹配 X/Y 条规则”，用于区分“服务没收到事件”和“Activity 不在白名单”。
- 新增 `Activity 不在规则白名单` 诊断日志；每个会话首次扫描记录节点数和最多 4 个“跳过类”候选的脱敏文本/class/bounds。
- 规则页补充 14 个 App 的中文名称。
- 版本号提升到 `0.1.2`（versionCode 3）。

### 0.1.1（2026-10-18）

- 日志磁盘上限从“2 × 256 KB”收紧到“2 × 128 KB”（总计约 256 KB）。
- 启动时清理旧版本留下的超大日志；轮转失败时删除/清空当前文件，防止无限增长。
- 日志页说明增加自动轮转和清空提示。
- 版本号提升到 `0.1.1`（versionCode 2）。

### 0.1.0（2026-10-18）

- 首个可安装版本：规则引擎、无障碍服务、安全门、dry-run、持久化日志。
- B 站真实点击验证通过；知乎真实点击验证通过。
- 修复同包重复启动不检测的问题：事件过滤加入桌面包，并增加同包会话重启兜底。
- 知乎规则收紧为必须带倒计时数字，避免裸“跳过”误候选。

## 12. 许可证与免责声明

- 本项目以 MIT License 开源，详见 [LICENSE](LICENSE)。
- 不使用 GKD、李跳跳等项目的商标、图标或界面素材；规则引擎为独立实现。
- 目标 App 的名称、图标、包名归各自权利人所有。
- 跳过广告可能违反目标 App 的服务条款；请仅在个人设备上用于减少误触，不用于商业用途，不绕过付费墙。
- 本项目按“现状”提供，不附带任何明示或暗示的担保；使用风险由使用者自行承担。
