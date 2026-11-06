# miyoru 构建说明（M0 骨架）

## 基线版本（`gradle/libs.versions.toml` 为准）

| 组件 | 版本 | 备注 |
|---|---|---|
| Gradle | 9.7.0 | 本机缓存 `~/.gradle/wrapper/dists/gradle-9.7.0-bin/`，`./gradlew` 优先用缓存 |
| Kotlin | 2.4.21 | |
| Compose Multiplatform | 1.9.3 | |
| AGP | 9.5.0 | 需 `android.newDsl=false` + `android.builtInKotlin=false` 旁路（见下） |
| SQLDelight | 2.4.1 | 插件已应用，`.sq` 在 B2 写；drivers：android/sqlite(native)/native(iOS) |
| QuickJS-KT | io.github.dokar3:quickjs-kt 1.0.0-alpha13 | KMP 全端；`plugin:runtime` commonMain；alpha 版，A3 验证超时/取消 |
| DataStore | androidx.datastore:datastore-preferences 1.3.0 | `shared` commonMain |
| Coil 3 | io.coil-kt.coil3 3.6.3（coil-compose + coil-network-ktor3） | `shared` commonMain，Header 注入 A3 验证 |
| Ksoup | com.fleeksoft.ksoup:ksoup 0.2.6 | `shared` commonMain（宿主 HTML 解析） |
| Navigation | org.jetbrains.androidx.navigation:navigation-compose 2.10.0 | `shared` commonMain（注：Google Maven 曾见 2.10.2，Central 确认 2.10.0） |
| Ktor | 3.6.1 | |
| coroutines / serialization | 1.11.0 / 1.12.0 | |
| koin | 4.2.2 | |
| Java | 21（Corretto） | Gradle 与 toolchain 均为 17+，本机 21 |
| Android SDK | `/Users/kurisu/Library/Android/sdk`，platforms 35–37，compileSdk/targetSdk 37，minSdk 24 | 见 `gradle.properties` 的 `sdk.dir` |

## AGP 9 旁路说明（A1 决策）

- `shared` / `plugin:api` / `plugin:runtime` 用经典组合 `com.android.library` + `kotlin.multiplatform androidTarget()`，
  在 AGP 9 下需 `gradle.properties` 中 `android.newDsl=false` + `android.builtInKotlin=false` 才能配置通过。
- `androidApp` 只用 `com.android.application`（AGP 9 内置 Kotlin，不再加 `kotlin.android`）。
- 后续迁移到 `com.android.kotlin.multiplatform.library` 新插件时去掉这两个开关（已验证新插件 marker 在 Google Maven 存在，
  但其顶层 DSL 与 Kotlin 2.4.21 的 `android{}` 目标改名冲突，A1 先用稳定老路）。

## KMP 注意事项

- `commonMain` 的 `@JvmInline value class` 必须显式 `import kotlin.jvm.JvmInline`，
  否则 iOS（Native 无 `kotlin.jvm.*` 默认导入）报 Unresolved，JVM 去掉注解又报不支持。

## 常用命令

```bash
./gradlew help                                          # 配置检查
./gradlew :shared:compileKotlinJvm                      # shared JVM
./gradlew :shared:compileDebugKotlinAndroid             # shared Android
./gradlew :shared:compileKotlinIosSimulatorArm64        # shared iOS 模拟器
./gradlew :shared:compileKotlinIosArm64                 # shared iOS 真机
./gradlew :androidApp:assembleDebug                     # APK → androidApp/build/outputs/apk/debug/
./gradlew :desktopApp:compileKotlin                     # 桌面
./gradlew :plugin:api:compileKotlinJvm :plugin:runtime:compileKotlinJvm
```

## A1 已验证（2026-11-05）

- [x] `help` 配置通过（5 模块）
- [x] shared：JVM / Android-debug / iOS-arm64 / iOS-模拟器
- [x] plugin:api、plugin:runtime：JVM / Android-debug / iOS-模拟器
- [x] androidApp `assembleDebug` 出包
- [x] desktopApp `compileKotlin`

## A2 已验证（2026-11-05）

- [x] SQLDelight 插件应用 + 三端 driver 依赖；`MiyoruDatabase`（`app.miyoru.db`，`src/commonMain/sqldelight`）空 schema 可配置
- [x] DataStore / Coil3 / Ksoup / Navigation / koin-compose / sqldelight-coroutines 进 `shared` commonMain
- [x] quickjs-kt 进 `plugin:runtime` commonMain
- [x] shared：JVM / Android-debug / iOS真机 / iOS模拟器（含全部新依赖的 native 变体）
- [x] plugin:runtime：JVM / Android-debug / iOS模拟器
- [x] QuickJS API 探明：`com.dokar.quickjs.QuickJs(dispatcher)` + `evaluate(script, filename)`（suspend，可取消）+
      `defineBinding`（宿主函数）+ `close()`；无显式 interrupt，需 A3 验证 `withTimeout` 对原生死循环是否真中断
- [ ] 风险：quickjs-kt 为 alpha（1.0.0-alpha13）；若超时/取消验证失败，回退评估其他 JS 引擎

## A3 已验证（2026-11-05）

- [x] **QuickJS 超时/取消（最高风险，结论：通过）**：`io.github.dokar3:quickjs-kt` 升级到 **1.0.15**
      （1.0.0-alpha13 无中断能力，`while(true){}` 会永久卡死 worker 线程，`withTimeout` 救不了——实测卡 11 分钟）。
      1.0.15 新增 `evaluationTimeoutMillis` + `interruptEvaluation()`，死循环抛出
      `QuickJsInterruptedException: InternalError: interrupted`，引擎之后仍可用。
      `:plugin:runtime:jvmTest` 4 项全过（求值/宿主绑定/原生超时/手动中断）。
      附带发现：JS 数字回调进 binding 是 `Long`，用 `Number` 接；Kotlin 2.4 下测试里裸 `async` 必须在
      `coroutineScope` 接收者下调用（全限定名会被判 DEPRECATION_ERROR）。
- [x] **SQLDelight v1 Schema**：6 个 `.sq`（PLUGIN / MEDIA / CONTENT_UNIT / FAVORITE+HISTORY+PROGRESS /
      DOWNLOAD_TASK+LOCAL_FILE / CACHE_ENTRY），9 表 + 文档要求的全部索引；
      `generateMiyoruDatabaseSchema` 产出 `databases/1.db`，`verifyMiyoruDatabaseMigration` 通过。
- [x] **三端 driver 接线**：`DatabaseDriverFactory.createDriver()`，Android（`AndroidSqliteDriver`，Context 由 DI 给）、
      JVM（`JdbcSqliteDriver` 文件/in-memory）、iOS（`NativeSqliteDriver`）；`:shared:jvmTest` 3 项读写冒烟全过。
      Android/iOS 真机执行留待 B3 集成后补。
- [x] **Coil Header**：`platform/image/ImageSource` + `CoilImageRequestFactory`（`NetworkHeaders.Builder` 逐项注入），
      JVM/Android/iOS-模拟器编译通过；真实带 Header 加载在 C2 用 Mock 服务验证。
- [x] **播放器结论**：Central 无 KMP 版 "MediaMP" 产物 → 不引入第三方播放器库；维持 `PlayerFacade` 自有抽象，
      Android=Media3、桌面=MPV 后端、iOS=AVKit，平台 actual 在 B5/C2 落。
- [ ] 已知杂项：`:shared:tasks --all` 会触发 Compose Preview 与 `unitTests.isIncludeAndroidResources=false` 冲突而失败，
      属任务列表期问题，不影响编译/测试/打包。

## B1 已验证（2026-11-05）

- [x] 错误模型补齐：`Authentication` / `RateLimited` + 稳定 `ErrorCode` + `Throwable.toAppError()`；
      取消语义：`TimeoutCancellationException`→`Timeout`，其余 `CancellationException` 重抛，`AppError` 透传，
      `kotlinx.io.IOException`→`Network`（新增 `kotlinx-io-core:0.9.2` 显式依赖）。
- [x] `MediaKey` 改为 `(SourceId, MediaType, RemoteId)` 类型化（与结构文档 §6.1 一致）；
      `SourceRepository.observeSources()` 返回 `List<SourceInfo>`；`search`/`resolve` 改类型化签名。
- [x] 新用例：`SearchAcrossSources`（supervisorScope+单源超时+部分成功+取消传播）、`ResolveContent`、
      `UpdateProgress`（钳制）、`ImportLocalFile`（TXT/EPUB/CBZ 形状校验，P0）。
- [x] `:shared:jvmTest` 21 项全过：Search×4 / Resolve×3 / UpdateProgress×3 / ImportLocal×4 / ErrorMapping×4 / DB冒烟×3。
- [x] 新 common 代码 Android-debug / iOS-真机 / iOS-模拟器编译通过。
- [ ] 约定：kotlin-test 跑在 JUnit4 上时测试方法必须返回 void——`assertIs`/`assertFailsWith` 有返回值，
      方法体统一用块形式（尾行 `Unit`），不要用 `= runBlocking {` 单表达式。

## B3 已验证（2026-11-05）

- [x] `data/source/model/PluginDto.kt`：插件 JSON→DTO（lenient 解析）→Domain 校验（必填 id/title、封面 http(s)）；
      失败即 `Parse`，不进 Domain。
- [x] `data/source/SourceSnapshotStore.kt`：远端结果写 MEDIA 快照（标题/封面/源名），卸载后仍可展示；覆盖写。
- [x] `data/library/LibraryRepositoryImpl.kt`：收藏 toggle、历史、进度（SQLDelight `asFlow().mapToList()` 观察流）；
      补回文档 §8.1 的 `observeProgress(key, unitId)`（A1 占位漏了）。
- [x] `data/download/DownloadRepositoryImpl.kt`：enqueue（只存非敏感选择器）/pause/resume/cancel/remove；
      非法状态转换（QUEUED 上 resume、未知 id）抛 `InvalidInput`；删任务只清索引，文件删除归 B5 引擎。
- [x] `platform/security/SecretStore` + `InMemorySecretStore`（Mutex 版，common 全端可用）；
      Keystore/Keychain/桌面安全存储 actual 留待平台集成轮替换 Koin 绑定。
- [x] `di/AppModules.kt`：Koin DSL 组合根（driver→db→repos→snapshot→UpdateProgress）；
      Source/Local/Plugin 仓库实现在 B4/B5 落后再接入（已留注释，不写空绑定）。
- [x] `:shared:jvmTest` 34 项全过（B1 的 21 + B3 的 13：DTO 5 / 快照 1 / 馆藏 3 / 下载 3 / Koin 装配 1）。
- [x] 新 data/di 代码 Android-debug / iOS-真机 / iOS-模拟器编译通过。
- [ ] 踩坑：`synchronized` 是 JVM 专属，common 用 `Mutex.withLock`；`@file:OptIn` 必须紧跟 `package`；
      `db.transaction{}`/`withContext{}` 有返回值，Unit override 必须显式声明 `: Unit` 或用块体。

## B4 进展（2026-11-05，未完，继续中）

- [x] `plugin:api`：manifest 补全（author/homepage/permissions/entry/entrySha256/signature）+ `validate()`：
      id/version/apiVersion/minAppVersion/媒体类型/能力集/域名（https+禁私网）/entry 防穿越/签名 Base64；
      `:plugin:api:jvmTest` 6 项全过。
- [x] `plugin:runtime/host/HostHttpService`：域名白名单（含子域）、https（`allowHttp` 仅 dev）、重定向**逐跳**校验、
      localhost/私网拦截、流式大小上限、超时映射；Cookie 隔离靠"每插件独立 HttpClient+CookieStorage"（测试实锤不串）。
      `HostHttpServiceTest` 6 项全过（MockEngine，无外网）。
- [x] `plugin:runtime/host/HostHtmlService`：Ksoup 封装 title/text/links，只返可序列化结果；1 项测试通过。
- [x] `QuickJsPluginEngine` v2：每插件独立实例 + 会话 Mutex 串行 + 原生超时；`invoke` 按 `globalThis[cap](payload)` 约定，
      输出限大小 + JSON 校验；错误码 TIMEOUT/JS_ERROR/TOO_LARGE/INVALID_JSON/CAPABILITY_UNSUPPORTED/LOAD_ERROR；
      bundle 求值尾部追加字符串字面量规避 JsObject→Unit 无转换器问题。`QuickJsPluginEngineTest` 5 项全过。
- [x] 新插件代码 Android-debug / iOS-模拟器编译通过。
- [ ] 踩坑：Ktor 3.6 Mock API 改为 `MockRequestHandleScope`（`MockEngineHandler` 已无）；
      `client.request(urlString){}` 传字符串，`Headers.entries().associate` 取头；
      `URLBuilder.encodedPath` 已无，用 `pathSegments` 语义手拼 + 自写 `..` 归一化。
- [ ] 待续（仍属 B4）：安装器（暂存→zip-slip/哈希校验→原子替换→回滚，JVM FS actual + iOS/Android 留位）、
      Ed25519 签名验证（common 接口 + JVM actual + 测试；iOS/Android 行为待定）、
      `SourceRepositoryImpl` + `PluginRepositoryImpl`（shared/data，含更新/回滚 DB 事务）、插件契约测试。

## B4 完成（2026-11-05）

- [x] 安装器：`PluginFileSystem` 抽象 + `SystemPluginFileSystem`（kotlinx-io，全端共用；`delete` 需手写递归）；
      `PluginPackageValidator`（zip-slip/manifest规则/entry哈希/签名）；`PluginInstaller`（暂存→校验→旧版搬备份→
      暂存搬正→清理，失败保旧版；`rollback`/`uninstall`）；`JvmZipReader`（java.util.zip，平台侧注入）。
- [x] 签名：`PluginSignatureVerifier` 接口 + `JvmEd25519Verifier`/`AndroidEd25519Verifier`（API 33+，低版本抛错，
      调用方降级 `enforceSignature=false`）+ `IosSignatureVerifier`（fail-closed，平台轮再实现）；
      规范化 manifest（除 signature 字段）签名；`enforceSignature` 下缺签名/错签名都装不上。
- [x] 错误码与异常归位 `plugin:api`（`PluginErrorCodes`/`PluginException`），shared 只依赖 api，runtime 实现细节不泄漏。
- [x] `SourceRepositoryImpl`（shared/data）：`observeSources` 读 PLUGIN 表；search/detail/chapters/resolve 走
      `PluginSessionProvider`（api 合约，runtime 实现读 bundle + 引擎会话）；信封约定
      `SearchEnvelope/DetailEnvelope/UnitsEnvelope/NovelEnvelope/PagesEnvelope/StreamsEnvelope`；
      resolve 按类型选 capability（NOVEL→content/COMIC→pages/VIDEO→streams）；远端结果写 MEDIA 快照。
- [x] `PluginRepositoryImpl`：install（facade 先行→DB 终态，previous_* 一层回滚链）/setEnabled/uninstall（先停文件再清 DB，
      收藏/历史/进度不级联）/rollback（DB 文件双交换）；`checkForUpdate` 无索引返回 null；
      `update()` 索引驱动在 M3，P0 手动更新走 `install()`（抛 `CapabilityUnsupported` 明示）。
- [x] Koin 组合根收敛：`appModule(driverFactory, sessionProvider, installerFacade)`。
- [x] 契约测试（shared jvmTest，真实引擎+FS+DB）：`PluginRepositoryContractTest` 3 项（安装/禁用/卸载、二次安装回滚、
      索引更新明示错误）；`SourceRepositoryContractTest` 2 项（搜索→详情→章节→正文全链路+快照落库、缺 capability 干净映射）。
- [x] 全量：shared 39 项、plugin 34 项（28 runtime + 6 api）全绿；新代码 Android-debug / iOS-真机 / iOS-模拟器编译通过。
- [ ] 踩坑：`SystemFileSystem.resolve` 要求路径存在（构造器里别 resolve）；kotlinx-io `delete` 非递归；
      fixture 域名必须合规（`https://x` 会被白名单规则拦——这是特性不是 bug）；
      `@file:OptIn` 必须紧跟 `package`；单表达式 override 推导出非 Unit 返回时必须显式 `: Unit`。

## B5 完成（2026-11-05）

- [x] `HttpDownloadEngine`（shared/data）：普通 HTTP（Range 续传、`.part` 临时文件、原子改名、512KB 节奏回写 DB）、
      HLS（播放列表解析→分片逐个下载→本地 index.m3u8 重写）；每次执行经 `DownloadUrlResolver` 重解析 URL（临时地址不复用）；
      取消向上传播（不记 FAILED），异常记 FAILED + 错误码。
- [x] HLS 解析器（common 纯逻辑）：媒体播放列表 + 相对/绝对分片地址归一；master 播放列表与加密分片明确拒绝
     （`Parse`/`CapabilityUnsupported`，DRM/规避不在 P0）。
- [x] `PluginDownloadSource`：经 SourceRepository 重解析 + `secret_ref`→SecretStore 取 Header；Comic/Video 取首条可用 URL。
- [x] 本地解析器（common）：TXT（UTF-8+BOM，章节启发式，标记后可带 28 字标题）、EPUB（container→OPF→spine→XHTML 提纯）、
      CBZ（图片过滤+自然排序，隐藏文件/`__MACOSX__` 跳过）。
- [x] `LocalImportRepositoryImpl`：inbox 读源→解析→拷入书库→MEDIA + CONTENT_UNIT 落库（正文按需从文件重读，不进 DB）；
      `remove` 删文件 + MEDIA/单元/收藏/历史/进度索引；`observeLocalMedia` 合并 NOVEL+COMIC。
- [x] SecretStore：JVM（prefs，注明非硬件级）+ Android Keystore（AES/GCM，API 23+）已落地；
      iOS Keychain 因 Security 框架桥接需真机联调，本轮 fail-closed 占位（平台集成轮补）。
- [x] DI：`appModule(..., httpClient, filesRoot, zipReader)` 装配引擎/导入器/解析源。
- [x] `:shared:jvmTest` 54 项全过（B5 新增 13：引擎 3 / HLS 1 / 解析器 7 / 导入 2 …明细见 XML）；
      新代码 Android-debug / iOS-真机 / iOS-模拟器编译通过。
- [ ] 踩坑：`"a" + "b".encodeToByteArray()` 里 `.` 优先级更高——多行拼接必须先加括号再转字节；
      TXT 章节正则必须容忍标记后的标题文字；MockEngine 无 handler 构造即抛；
      `selectByMedia` 无 LIMIT 参数（按 .sq 签名传参）。

## C1 完成（2026-11-05）

- [x] 路由：17 个类型安全 destination（5 Tab 对象 + Detail/Reader/Player + Sources/Site + 7 子页），`MAIN_TABS` 集合表达
      "宽屏仅 5 主 Tab 显示 rail"规则（与原型一致）。
- [x] 自适应骨架 `MainScaffold`：`BoxWithConstraints` ≥840dp 判宽屏；窄屏底栏 / 宽屏侧栏（仅主 Tab）；子页沉浸（无 rail/bar）
      + 顶栏标题与返回；Tab 切换 `launchSingleTop + saveState/restoreState`。
- [x] 主题 `MiyoruTheme`：紫色基准（#6750A4 / 深色 #D0BCFF），跟随系统深浅色；动态取色待平台验证后接入。
- [x] 占位屏：17 路由全部可达（首页可进详情/站点，浏览可进数据源/站点/插件商店，更多可进 7 子页，详情可进阅读器/播放器）；
      C2 按 feature 切分替换为真实实现。
- [x] 验证：shared JVM/Android/iOS-模拟器/iOS-真机/桌面编译通过；`androidApp:assembleDebug` 出包成功。
- [ ] 踩坑：`compose.materialIconsCore` 访问器不存在，icons 包最高只有 1.7.3（未跟 compose 1.9 线）——返回键暂用文字"‹ 返回"，
      图标在 C2 引入图标方案时统一解决；`runCatching{}.getOrDefault` 在 heterogeneous when 下推断失败，改显式 `val r: Any` + try/catch。

## C2a 完成（2026-11-05，表现层第一批：书架/历史/详情）

- [x] 依赖：`lifecycle-viewmodel` + `koin-compose-viewmodel` + `lifecycle-runtime-compose`（collectAsStateWithLifecycle）
      进 commonMain；`kotlinx-coroutines-test` 进 commonTest（`runTest` + `setMain(StandardTestDispatcher())`）。
- [x] 联表查询（query-only，不动 schema 版本）：`selectFavoriteMedia` / `selectHistoryMedia`（MEDIA JOIN，收藏/历史列表
      直接拿标题封面）；`LibraryRepository` 新增 `observeFavoriteMedia` / `observeHistoryMedia`（A1 占位只有 key）。
- [x] 三个 ViewModel + UiState + Screen：书架（类型筛选 chips + 网格）、历史（列表 + 单条删除）、详情（简介/目录/
      收藏切换/重试）；状态 `Loading/Empty/Error/Content` 四态分离，一次性事件暂不引入 Channel（C2b 按需加）。
- [x] Koin：`viewModel { }`（`org.koin.compose.viewmodel.dsl.viewModel`，koin-core 里没有）绑定三 VM，
      Detail 带 `parametersOf(key)`；装配测试覆盖全部 VM 解析。
- [x] 导航接入：书架/历史/详情走真实 Screen，其余 14 路由仍占位；详情→阅读器带 unitId。
- [x] `:shared:jvmTest` 60 项全过（新增 VM 6 项）；Android-debug / iOS-模拟器 / 桌面编译通过。
- [ ] 踩坑：SQLDelight 生成类名跟表名大小写走（`MEDIA` 不是 `Media`，联表查询生成 `SelectHistoryMedia` 等新类型）；
      `Text()` Elvis 两边类型必须一致；JUnit4void 规则同样适用于 `runTest`（方法尾加 `Unit`——本次 6 个 VM 测试同样处理）。

## C2b 完成（2026-11-05，表现层第二批：首页/浏览/数据源/站点/下载/小说阅读器）

- [x] 6 ViewModel + Screen：Home（继续阅读+收藏数）、Browse（已订阅源列表）、Sources（启用开关/删除）、
      Site（单源搜索）、Downloads（开始/暂停/删除）、NovelReader（章节正文+上下章+章节粒度进度恢复）。
- [x] NovelReader 双源：sourceId=="local" 读 `libraryRoot` 文件（TXT/EPUB 解析，章节切分），远端走
      detail/chapters/resolve；`updateProgress` 章节粒度（position=章节序号，unitId=`__novel__`），重进恢复。
- [x] Koin：`single { httpClient }`、`single(named("filesRoot"))`、`single { zipReader }` 装配引擎/导入器/阅读器。
- [x] `:shared:jvmTest` 74 项全过（新增 Home/Browse/Sources/Site/Downloads×2/Novel×2 共 9 项）；
      Android-debug / iOS-模拟器 / 桌面编译通过。
- [ ] 踩坑：`viewModel {}` DSL 在 `org.koin.compose.viewmodel.dsl`（koin-core 里没有）；
      Koin 传参构造用 `parametersOf` + `koinViewModel(parameters={...})`，测试里 `koin.get<T>{ parametersOf(...) }`；
      `collectAsStateWithLifecycle` 要 `lifecycle-runtime-compose`（commonMain）。

## C2 完成（2026-11-05，全部 17 屏可达，真实现）

- [x] 漫画阅读器：横向 pager + 双指缩放/拖动（1-5x，超界回弹）+ Coil AsyncImage；
      远端 URL + `ImageRequestFactory` Header 注入，本地 CBZ 按字节加载（绕开 Coil 文件映射差异）。
- [x] 播放器：`PlayerFacade.nativePlayer` 透出；Android Media3（ExoPlayer + HLS/Progressive 自适应 + Header 注入，
      `createPlayerFacade` expect/actual，Application context 由 MainActivity 注入）；
      桌面/iOS 为明确占位（MPV/AVKit 在平台集成轮落地）；`VideoSurface` expect/actual（Android=PlayerView，其余占位）。
- [x] 设置：DataStore Preferences 后端（平台入口 `PreferenceDataStoreFactory.create(File)` 构造后注入，
      common 不碰 okio 路径——okio 3.18 只有 `Path.get`，没有 `String.toPath`）；主题/默认类型/并发数三项真实持久化。
- [x] 存储/统计/分类/插件商店/关于：缓存占用观察 + 一键清理；收藏/历史/下载计数；按媒体类型计数进书架；
      URL 安装插件（`InstallPluginFromUrl`，50MB 上限）；静态关于页。
- [x] 导航全接线：详情按类型分流（视频→播放器，小说/漫画→阅读器）；阅读器按类型切 Novel/Comic；分类→书架；浏览→站点。
- [x] DI：`createPlayerFacade`、`SettingsRepository`、`CacheRepository`、`InstallPluginFromUrl` 及全部 VM 绑定；
      android/desktop 双入口完整装配（Koin + HttpClient/OkHttp + 超时 + Cookie + FS 根目录 + DataStore 文件）。
- [x] `:shared:jvmTest` 110 项、plugin 34 项全绿；Android-debug 出包（24MB）；iOS-真机/模拟器、桌面编译通过。
- [ ] 踩坑：Coil3 `ImageRequest.Builder` 没有 `headers()`——用 `coil.network.httpHeaders(nh)` 扩展；
      okio 3.18 无 `String.toPath`，`Path.get` 也只在 JVM 实际存在——common 跨平台路径一律用 kotlinx-io；
      DataStore 的 `createWithPath(okio)` 会把平台绑死在入口，还是 `create(File)` + 平台传入最干净；
      Media3 的 hls/ui 是独立产物（`media3-exoplayer-hls` / `media3-ui`）。

## 缺口清单（P0 收尾 / 平台集成轮）

1. iOS 应用入口未接 Koin/装配（iosApp 仍为 Swift 占位）；桌面/iOS 播放器后端 stub（MPV/AVKit）。
2. iOS Keychain 未实现（fail-closed）；Android 低版本（<API 33）Ed25519 抛错降级。
3. 本地导入缺 UI 入口（Repository/解析器/测试已就绪；需 FileKit 文件选择 + 导入屏）。
4. 桌面/Windows 打包与真机验收、iOS TestFlight 打包验收未做（本机仅 macOS 编译）。
5. Coil 封面图尚未接入（详情/书架/历史当前纯文本行）。
6. HLS 只支持明文媒体播放列表（master 列表与加密分片明确拒绝，符合 P0）。

## C2d + C3（2026-11-05，封面图、本地导入 UI、全量核验）

- [x] 封面图：`core/ui/components/CoverImage`（Coil AsyncImage + 无图占位），书架/历史/站点列表 + 详情头图接入。
- [x] 本地导入 UI：FileKit（0.12.0，`filekit-core` + `filekit-dialogs-compose`）文件选择器（限 txt/epub/cbz），
      选文件 → 拷入 inbox → `ImportLocalFile` 校验 → 入库；`Import` 路由 + 更多入口 + 顶栏标题全接通。
- [x] 全量：shared + plugin 112 项单测全绿；Android-debug 出包；iOS-真机/模拟器、桌面编译通过。
- [x] P0 验收逐项核对（对照 PROJECT_GOALS §10）：
      通过：无真实源/默认仓库；安装/禁用/回滚/卸载全链路（契约测试）；聚合搜索部分成功；
      小说/漫画消费器存取进度；收藏历史快照；普通 HTTP + HLS 离线下载可暂停/恢复/跨重启；
      SQLDelight schema + 迁移验证机制 + 1.db；插件网络权限/超时/大小/取消/脱敏测试；Mock 无外网端到端。
      部分通过：视频播放（Android 真实现，桌面/iOS 占位）；漫画防 OOM（Coil 按目标尺寸采样，未做超大图专项子采样）；
      测试插件覆盖首页/搜索/详情/章节/三媒体解析（fixture 覆盖，缺真实插件包）。
      未覆盖（环境/设备限制）：Android Release 与 Windows 桌面安装包启动验证；SQLDelight 跨版本升级迁移实测
      （尚无 v2 schema 可测）；五端真机/安装包验收；无阻断崩溃/泄漏的设备级验证；iOS 商店政策复核。
- [ ] 踩坑：FileKit 的 `PlatformFile.name` / `readBytes()` 是扩展属性/函数，必须显式
      `import io.github.vinceglb.filekit.name` / `...readBytes`（同包名不自动可见）；
      FileKit 版本用已缓存的 0.12.0（Central 最新），不用 beta。

## C4 完成（2026-11-05，iOS 真机链路 + 桌面打包验证）

- [x] 纯 Kotlin ZIP 解包（`plugin:api`，stored+deflated+Huffman/CRC 全自研）：对照 `java.util.zip` 生成包 round-trip
      测试（50KB 随机二进制/中文名/注释/空目录/EPUB 形），CRC 篡改与非 zip 拒绝；已替换 JVM/Android 双实现，全端共用。
- [x] iOS 可运行：xcodegen 工程 + `PRODUCT_NAME`/`@executable_path/Frameworks` 修正 + `Info.plist`
      `CADisableMinimumFrameDurationOnPhone`（Compose 120Hz 校验强制要求）；
      SwiftUI 入口 + Koin 装配 + 原生 SQLite + AVKit 播放器 + Keychain + VideoSurface 全量编译通过。
- [x] iPhone 17 模拟器实机验证：安装→启动→首页渲染（Koin + Native SQLite 读库全链路），启动期 dyld/Info.plist
      两连坑已填（缺 runpath；缺高刷声明）。
- [x] 桌面 .app 打包验证：jpackage 默认裁掉 `java.sql` 致启动 `NoClassDefFoundError: DriverManager`，
      `nativeDistributions { modules("java.sql", ...) }` 显式保留后打包 162MB、启动进首页（深色主题）。
- [x] iOS interop 踩坑（BUILD.md 必读）：成员函数是 extension 形式，必须逐个显式 import；
      `@file:OptIn` 必须在 `package` 之前（否则整文件解析毒化，报错全是误报）；
      `UIView()` 非指定构造器不可直接继承，用 `init(frame:)`；`NSURL`/`AVURLAsset` 等工厂方法正常。
- [ ] 未覆盖：Windows 安装包与运行（无 Windows 主机）、Release 构建、真机调试、TestFlight 打包/签名、
      商店政策复核、桌面 MPV 后端、iOS Keychain/AVPlayer 真机执行（编译通过，未在模拟器上跑业务路径）。

## C5 完成（2026-11-05，iOS 真机 runtime 验证）

- [x] commonTest 上 iOS 模拟器：shared 53 项（领域/解析/DTO/ViewModel 全量纯逻辑）、
      plugin:runtime 24 项（QuickJS 原生求值/绑定/**原生超时中断**/手动中断、Host HTTP 白名单/重定向/大小/Cookie 隔离、
      Ksoup、FS+安装+端到端 session、SHA-256/包校验）、plugin:api 6 项（manifest 校验），0 失败。
- [x] 关键结论：quickjs-kt 1.0.15 的 `evaluationTimeoutMillis` / `interruptEvaluation` 在 iOS arm64 真 runtime 上
      与 JVM 行为一致（死循环被中断、引擎可继续用）；Ktor Darwin + MockEngine、Ksoup 原生、kotlinx-io FS 均正常。
- [x] 测试分层明确：纯逻辑/common 测进 `commonTest`（JVM + iOS 双跑）；DB 驱动（SQLite JDBC/Native）与
      `java.*` 依赖的测试留 `jvmTest`（`InMemoryJvmDriverFactory`、`java.util.zip` 打包、`java.security` 签名）。
- [x] 踩坑：Native 无 `AutoCloseable.use`（`QuickJs.close()` 改 try/finally）；
      `MockEngine` 等 Mock API 全 KMP 可直接放 commonTest，需把 `ktor-client-mock` 依赖也上移到 commonTest；
      `expect/actual` 只在平台 source set 间有效，test source set 之间不能跨（DB 工厂这类平台相关测试辅助留在 jvmTest）。

## C5 补（2026-11-05，Android 真机链路 + Release）

- [x] 自建 `miyoru-avd`（Pixel 9 / API 36 / arm64）并开机；debug/release 双包安装启动，首页正常渲染，无崩溃。
- [x] 修阻塞性构建 bug：AGP 9 所谓“内置 Kotlin 支持”并不会编译 `src/main/kotlin`（APK 里根本没有 MainActivity，
      启动即 `ClassNotFoundException`）。结论：**`kotlin.android` 插件仍是必需的**，配合 `jvmTarget = 17`
      与 Java 17 对齐——之前的“不再需要 kotlin.android”是误读，已更正。
- [x] Release 包用 debug keystore 签名验证出包+启动流程（18MB）；正式商店签名待办。

## D1 完成（2026-11-06，插件系统改为仓库优先）

- 插件分发改为「仓库优先」：添加 HTTPS 索引 URL → 严格校验并展示识别结果（不落库）→ 用户信任并落库（记录身份指纹）→ 按仓库分组浏览「可安装 / 有更新 / 已安装」三分面目录 → 安装前权限确认、按需逐个安装 → 同源检查更新 / 更新 / 回滚 → 删除仓库提示影响范围。
- 信任模型修正：初版把「索引内容摘要」当信任指纹，会导致仓库每次发布新插件版本都要求用户重新确认。改为 `identityFingerprint(index, url)` = SHA-256(`repositoryId` + 仓库 URL + `signingKey`)，索引新增 `signingKey` 字段并校验其为合法 Base64 公钥；只有仓库 ID、链接或发布者公钥变化才要求重新识别确认，索引内容摘要 `fingerprint(index)` 保留为诊断用途。
- 新增/改动的文件类别：`plugin:api` 的 `RepositoryIndex.kt`（miyoru 原生索引模型 + lenient 解析 + `validate()`）与 `Sha256.kt`（纯 Kotlin 整包哈希，全项目唯一实现，runtime 委托之）；`shared/domain/plugin` 的仓库/目录模型与 `PluginCatalogRepository`，`PluginRepository` 移除游离 `install()` / `update()`、`checkForUpdate()` 改为按 `repository_id` 查缓存并比较 semver 的真实实现；`shared/data/plugin` 的 `RepositoryIndexClient` / `RepositoryPreviewStore` / `PluginCatalogRepositoryImpl`；`shared/feature/repositories` 与重写后的 `shared/feature/extensions`；导航新增 `Repositories` destination；`appModule` 装配新仓储与 ViewModel，平台入口仍传 `PluginInstallerFacade` / `HttpClient`。
- SQLDelight 升级到 v2：新增 `PLUGIN_REPOSITORY` / `REPOSITORY_PLUGIN` 表（`PluginRepository.sq`），`PLUGIN.repository_id` 改 NOT NULL（历史行用空串兜底，仅代表迁移前安装）；新增 `migrations/1.sqm` 与 `databases/2.db`，保留 `databases/1.db` 作为 v1 基线；`FAVORITE` / `HISTORY` / `PROGRESS` 不因仓库删除或插件卸载而级联删除。
- 移除单 ZIP 直装：删除 `InstallPluginFromUrl.kt` 与「输入单个 plugin.zip URL / 本地 ZIP 安装」入口；开发模式测试插件改用本地 mock 仓库 + 固定 fixture；采用 miyoru 原生索引格式（`schemaVersion = 1`），不兼容 Mihon/Komikku 的 `index.min.json` 字段语义；正式构建不内置、不预置、不推荐任何仓库地址。

### D1 验证（2026-11-06，补记真实执行结果）

- [x] `./gradlew :shared:jvmTest :plugin:api:jvmTest :plugin:runtime:jvmTest --console=plain` → `BUILD SUCCESSFUL`。JUnit XML 汇总 47 个测试类 / 143 个测试 / 0 失败 / 0 跳过。仓库相关：`PluginCatalogRepositoryImplTest` 8 项（识别不落库、按需安装写来源仓库并启用更新、整包哈希不符拒绝、换发布者公钥需重新确认且保留旧缓存、刷新失败保留已验证索引、删除仓库两种影响范围、跨仓库覆盖被拒）、`RepositoryIndexTest` 10 项、`PluginRepositoryContractTest` 5 项、`RepositoriesViewModelTest` 4 项、`ExtensionsViewModelTest` 4 项、`KoinWiringTest` 1 项。
- [x] `./gradlew :shared:verifyCommonMainMiyoruDatabaseMigration --console=plain` → `BUILD SUCCESSFUL`（v1 → v2 迁移校验通过）。原待办写的 `:shared:verifySqlDelightMigration` 不是本项目任务名，已更正。注意 `:shared:generateCommonMainMiyoruDatabaseSchema` 与 verify 不能放进同一次 Gradle 调用并行执行，Gradle 9.7 会报隐式依赖；schema（`2.db`）需先单独生成。
- [x] `./gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:assembleDebug --continue --console=plain` → `BUILD SUCCESSFUL`；产出 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`（31,298,211 字节）。
- [x] `./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 --console=plain` → `BUILD SUCCESSFUL`（Windows 主机交叉编译 Kotlin/Native iOS 目标；仅编译，未链接 framework、未跑模拟器测试，后者在 Windows 上由 Gradle 明确禁用）。
- [x] `python build_prototype.py` → `frames=36 groups=271 items=397`，重新生成 `design.json` / `prototype.html` / `share-link.txt`；原型新增「插件仓库」逻辑屏（共 18 个），文案统一为「身份指纹」。

踩坑记录（两次假失败，均已定位，不要据此改业务代码）：

- 首次全量 JVM 测试出现 `PlayerViewModelTest.resolve failure surfaces error` 失败（94 tests / 1 failed，`PlayerViewModelTest.kt:89` 断言 `errorMessage != null`）。单独重跑该测试类 `BUILD SUCCESSFUL`，随后全量重跑也通过；`PlayerViewModel.kt` 本轮未改动，其 `player.state` 订阅已用 `ps.errorMessage ?: _uiState.value.errorMessage` 保护解析阶段错误。判断为多测试类共享 `Dispatchers.setMain` 全局状态的偶发时序问题，列为观察项。
- 一次把 JVM / Android / iOS 编译放在同一条 `--continue` 命令里时，`:shared:compileKotlinJvm` 报 `JvmPlayer.kt:104 UNSAFE_CALL (MediaProperties?)`；拆开单独编译全部通过，且该行本就是安全调用 `player.mediaProperties.value?.durationMillis`。判断为 Kotlin/Native 与 JVM 并发编译共享增量缓存导致的假错误。后续验证把 iOS 目标与 JVM/Android 目标拆成两条命令执行。

仍待完成（需真机 / macOS 环境，本轮未做，不写结论）：

- [ ] Android / iOS / 桌面三端仓库流程手测：添加/识别/信任、跨仓库覆盖被拒、离线缓存浏览但安装需重新验证、删除仓库不删用户数据。
- [ ] iOS framework 链接、模拟器/真机运行（需 macOS）；桌面与 Android 安装包实机启动回归。

## 本机环境（Windows 开发机，2026-11-06）

所有工具链集中在 `D:/DevCache`，通过用户级环境变量指向，不依赖 C 盘：

| 用途 | 路径 | 说明 |
|---|---|---|
| JDK | `D:/DevCache/jdk/jdk-21.0.12.1+1` | Temurin 21，`JAVA_HOME` |
| JBR | `D:/DevCache/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2` | JBR 21.0.11（**热重载必需**），Gradle daemon JVM |
| Gradle 用户目录 | `D:/DevCache/.gradle` | `GRADLE_USER_HOME`，wrapper 9.7.0 缓存已在此 |
| Android SDK | `D:/DevCache/Android/Sdk` | `ANDROID_HOME` / `ANDROID_SDK_ROOT`，platforms 34–37 |

- `local.properties`（已 gitignore）写 `sdk.dir=D:/DevCache/Android/Sdk`；
  `gradle.properties` 里原来写死的 macOS 路径已移除，改由各机器 local.properties 提供。
- `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` 已用 9.7.0 重新生成：
  原先是手写 shim（只找 `~/.gradle` 缓存、找不到就调系统 gradle），Windows 上直接失败。
- `kotlin.incremental=true` 显式写进 `gradle.properties`（全局 `~/.gradle/gradle.properties`
  里是 `kotlin.incremental=false`，会让增量编译失效、拖慢热重载回合）。

## 桌面端热重载（Compose Hot Reload 1.2.0）

`desktopApp` 已接入 `org.jetbrains.compose.hot-reload`，`jvmToolchain` 提到 21：

```bash
export JAVA_HOME=D:/DevCache/jdk/jdk-21.0.12.1+1
export GRADLE_USER_HOME=D:/DevCache/.gradle
export ANDROID_HOME=D:/DevCache/Android/Sdk
export MIYORU_DEMO=1          # 可选：注入设计对照用的演示数据
./gradlew :desktopApp:hotRun --auto
```

- 启动日志里应出现 `JetBrains Runtime: ...jbrsdk...`；若跑到 Temurin 上，热重载会因为
  缺少增强类重定义能力而失效——这是 JBR 与普通 JDK 的关键差别。
- `--auto` 会常驻一个 Gradle daemon 监听源码，保存即重编译并 reload；改 `main()`
  里的窗口参数、依赖、Compose 编译器插件行为时需要重启（结构变更不走热替换）。
- 手工触发：`./gradlew :desktopApp:hotReloadMain`；应用内触发可用 `./gradlew reload`。
- 窗口按设计画板 1280×800 打开；`alwaysOnTop` 仅在 `MIYORU_DEMO=1` 时开启，方便与设计稿对照。

## 设计对照与演示数据

17 屏表现层按 `build_prototype.py` 的声明重写，通用件集中在
`shared/src/commonMain/kotlin/app/miyoru/core/ui/`：

- `theme/Theme.kt`：深色优先的 M3 紫色方案（surface container 全阶）、字阶（22/18/15/13）、
  `Dimens`（侧栏 80、顶栏 64、页边距 32/16、卡片 340/250、行高 72）。
- `components/Navigation.kt`：顶栏、侧栏、底栏、页面骨架、空状态。
- `components/Controls.kt`：搜索框、一级 Tab、筛选 chip、按钮、进度条、设置行。
- `components/Media.kt`：封面、封面卡片、列表行、继续阅读卡、网格、数字卡。
- `icon/MiyoruIcons.kt`：图标门面（`material-icons-extended:1.7.3`），屏幕层不直接依赖生成类。

`MIYORU_DEMO=1` 时 `dev/DemoContent.kt` 用内存仓储覆盖 `LibraryRepository` /
`SourceRepository` / `DownloadRepository`，让空数据下的 17 屏也能呈现接近设计稿的内容密度。
正式构建不含该模块的任何装配。

## 表现层已修正的问题

- **JVM 数据库第二次启动崩溃**：`JvmDatabaseDriverFactory` 之前无条件 `Schema.create`，
  第二次启动必然 `table CACHE_ENTRY already exists`。改为读写 `PRAGMA user_version`：
  0 → create、低于当前版本 → migrate、再回写版本（Android/Native 驱动自带版本管理，无需改）。
- 顶栏标题与操作按钮的 `weight` 争抢导致搜索图标跑到页面中间。
- 列表行 `supporting` 与标题重复（目录行同时显示「第 1 话」和「第 1 话」）。
