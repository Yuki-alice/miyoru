#!/usr/bin/env python3
"""按 M3E Canvas 的 agent.md 文档格式生成设计原型，并输出分享链接。

改完 SCREENS / 页面定义后重跑即可：
    python3 build_prototype.py

产物：
    design.json       设计文档，可在画布里用 Open project 打开
    prototype.html    一键打开原型 / 复制分享链接 / 设计说明
    share-link.txt    纯链接文本
"""
from __future__ import annotations

import base64
import json
import pathlib
import zlib

ROOT = pathlib.Path(__file__).resolve().parent
BASE = "https://lnkiai.github.io/m3e-canvas/"

_counter = [0]


def uid(prefix: str = "i") -> str:
    _counter[0] += 1
    return f"{prefix}{_counter[0]}"


def item(kind: str, label: str = "", icon: str | None = None, variant: str = "filled", **kw):
    # 编辑器对可选数字字段要求 undefined 或有限数；null 会被判为非法并拒绝打开文档
    d = {"id": uid(), "kind": kind, "label": label, "icon": icon, "variant": variant}
    d.update({k: v for k, v in kw.items() if v is not None})
    return d


def group(x: float, y: float, items, axis: str = "x") -> dict:
    if not isinstance(items, list):
        items = [items]
    return {"id": uid("g"), "x": x, "y": y, "axis": axis, "items": items}


def act(to: str, transition: str = "slide") -> dict:
    return {"to": to, "transition": transition}


NAV = [
    {"icon": "home", "label": "首页"},
    {"icon": "menu_book", "label": "书架"},
    {"icon": "history", "label": "历史"},
    {"icon": "explore", "label": "浏览"},
    {"icon": "more_horiz", "label": "更多"},
]
MAIN = ["home", "library", "history", "browse", "more"]


def resolver(wide: bool):
    def t(key: str) -> str:
        if key == "back":
            return "back"
        return key + ("_w" if wide else "")
    return t


def nav_actions(selected: int, t) -> dict:
    return {f"tab:{i}": act(t(MAIN[i]), "fade") for i in range(5) if i != selected}


class Screen:
    """一个 frame，以及它下面所有组件的画布绝对坐标。"""

    def __init__(self, doc, key, name, ox, oy, w, h, note, bg=None):
        self.doc, self.ox, self.oy = doc, ox, oy
        fr = {"id": key, "name": name, "x": ox, "y": oy, "note": note}
        if (w, h) != (412, 892):
            fr["w"], fr["h"] = w, h
        if bg:
            fr["bg"] = bg
        doc["frames"].append(fr)

    def add(self, x, y, parts, axis="x"):
        self.doc["groups"].append(group(self.ox + x, self.oy + y, parts, axis))


class Flow:
    """垂直流式排版：同一份页面定义自动适配 412 手机与 1280 宽屏。"""

    def __init__(self, screen, t, wide, *, nav_phone=False, nav_wide=False, selected=None):
        self.s, self.t, self.wide = screen, t, wide
        self.nav_wide = nav_wide
        self.left = 112 if (wide and nav_wide) else (32 if wide else 16)
        self.cw = 1136 if (wide and nav_wide) else (1216 if wide else 380)
        self.cursor = 64 if wide else 88
        if nav_phone and not wide:
            screen.add(0, 788, item("bottomNav", "", None, tabs=NAV, selected=selected,
                                    actions=nav_actions(selected, t)))
        if wide and nav_wide:
            screen.add(0, 0, item("navRail", "", None, tabs=NAV, selected=selected,
                                  size2=800, actions=nav_actions(selected, t)))

    def add(self, x, y, parts, axis="x"):
        self.s.add(x, y, parts, axis)

    def place(self, x, y, parts, axis="x"):
        self.add(x, y, parts, axis)

    def topbar(self, label, icon2=None, back=False, note=None):
        x = (80 if self.nav_wide else 0) if self.wide else 0
        size = (1200 if self.nav_wide else 1280) if self.wide else None
        kw = {"icon2": icon2, "size": size, "note": note}
        if back:
            kw["icon"] = "arrow_back"
            kw["actions"] = {"icon": act("back", "slideLeft")}
        else:
            kw["icon"] = None
        self.add(x, 0, item("topAppBar", label, **kw))

    def tabs(self, labels, selected=0, note=None, gap=16):
        x = 0 if not self.wide else self.left
        size = self.cw if self.wide else None
        self.add(x, self.cursor, item("tabs", "", None, size=size, selected=selected,
                                      tabs=[{"icon": "", "label": l} for l in labels], note=note))
        self.cursor += 48 + gap

    def search(self, placeholder, note=None, gap=16):
        self.add(self.left, self.cursor, item("searchBar", placeholder, None, icon2="search",
                                              size=self.cw if self.wide else None, note=note))
        self.cursor += 56 + gap

    def title(self, text, size=18, gap=12):
        self.add(self.left, self.cursor, item("text", text, None, size=size, bold=True))
        self.cursor += round(size * 1.3) + gap

    def text(self, x, y, s, size=15, bold=False):
        self.add(x, y, item("text", s, None, size=size, bold=bold))

    def chips(self, labels, checked=0, gap=16):
        parts = []
        for i, entry in enumerate(labels):
            label, icon = (entry, None) if isinstance(entry, str) else entry
            parts.append(item("chip", label, icon, checked=(i == checked)))
        # 窄屏筛选 chip 超过 4 个就换行，避免整行横排溢出手机屏幕
        per_row = 8 if self.wide else 4
        rows = [parts[i:i + per_row] for i in range(0, len(parts), per_row)]
        for r, row in enumerate(rows):
            self.add(self.left, self.cursor + r * 40, row, axis="x")
        self.cursor += 40 * (len(rows) - 1) + 32 + gap

    def rows(self, specs, gap=16):
        parts = []
        for sp in specs:
            kw = dict(sp)
            to = kw.pop("to", None)
            if to:
                kw["action"] = act("back" if to == "back" else self.t(to))
            size = kw.pop("size", self.cw if self.wide else None)
            if size is not None:
                kw["size"] = size
            parts.append(item("listItem", kw.pop("label", ""), kw.pop("icon", None), **kw))
        self.add(self.left, self.cursor, parts, axis="y")
        self.cursor += 72 * len(parts) + gap

    def grid(self, works, card_h=None, gap=16, no_image=False):
        cols = 4 if self.wide else 2
        card_w = (self.cw - gap * (cols - 1)) // cols
        card_h = card_h or (340 if self.wide else 250)
        for i, w in enumerate(works):
            kw = dict(w)
            to = kw.pop("to", None)
            if to:
                kw["action"] = act("back" if to == "back" else self.t(to))
            kw.setdefault("size", card_w)
            kw.setdefault("size2", card_h)
            if no_image:
                kw["noImage"] = True
            r, c = divmod(i, cols)
            self.add(self.left + c * (card_w + gap), self.cursor + r * (card_h + gap),
                     item("card", kw.pop("label", ""), kw.pop("icon", None), **kw))
        self.cursor += ((len(works) + cols - 1) // cols) * (card_h + gap)

    def buttons(self, specs, gap=16):
        n = len(specs)
        bw = 240 if self.wide else (380 - 16 * (n - 1)) // n
        parts = []
        for sp in specs:
            kw = dict(sp)
            to = kw.pop("to", None)
            if to:
                kw["action"] = act("back" if to == "back" else self.t(to))
            kw["size"] = bw
            kw.setdefault("variant", "filled")
            parts.append(item("button", kw.pop("label", ""), kw.pop("icon", None), **kw))
        self.add(self.left, self.cursor, parts, axis="x")
        self.cursor += 56 + gap

    def carousel(self, labels, h, note=None, gap=16):
        x = self.left if self.wide else 0
        size = self.cw if self.wide else 412
        self.add(x, self.cursor, item("carousel", "", None,
                                      tabs=[{"icon": "", "label": l} for l in labels],
                                      count=len(labels), layout="multiBrowse",
                                      size=size, size2=h, note=note))
        self.cursor += h + gap

    def progress(self, value, note=None, gap=16, wavy=True):
        self.add(self.left, self.cursor, item("linearProgress", "", None,
                                              size=self.cw if self.wide else None,
                                              value=value, wavy=wavy, note=note))
        self.cursor += 24 + gap

    def panel(self, label, supporting=None, h=110, note=None, gap=16):
        self.add(self.left, self.cursor, item("card", label, None, supporting=supporting,
                                              noImage=True, size=self.cw if self.wide else None,
                                              size2=h, note=note))
        self.cursor += h + gap

    def box(self, x, y, w, h, fill, note=None):
        self.add(x, y, item("box", "", None, size=w, size2=h, fill=fill, note=note))

    def image(self, x, y, w, h, note=None):
        self.add(x, y, item("image", "", None, size=w, size2=h, note=note))

    def toolbar(self, x, y, tabs, note=None, variant="tonal"):
        self.add(x, y, item("toolbar", "", None, variant=variant,
                            tabs=[{"icon": i, "label": ""} for i in tabs], note=note))


def list_page(title, *, icon2=None, back=True, tabs=None, search=None, chips=None,
              sections=(), trailing=None, note=None):
    """列表型页面的声明式工厂。sections 为 (小标题, 行定义列表) 的序列。"""
    def build(f, t):
        f.topbar(title, icon2=icon2, back=back, note=note)
        if tabs:
            f.tabs(tabs)
        if search:
            f.search(search)
        if chips:
            f.chips(chips)
        for heading, rows in sections:
            if heading:
                f.title(heading)
            f.rows(rows)
        if trailing:
            trailing(f, t)
    return build


# ---------------------------------------------------------------- 主导航五屏

def page_home(f, t):
    f.topbar("首页", icon2="search")
    f.search("搜索作品、作者或来源",
             note="统一搜索漫画、小说与视频；结果按内容类型分组，支持按来源过滤。")
    f.tabs(["全部", "漫画", "小说", "视频"],
           note="按内容类型切换首页内容；三种类型共用同一套书架、进度与追更逻辑。")
    f.title("继续阅读", 22)
    resume = ["《长夜余火》· 第 12 话", "《山海拾遗》· 第 3 卷",
              "《黎明之前》· EP06", "《雾中列车》· 第 07 话"]
    f.carousel(resume[:4] if f.wide else resume[:3], h=230 if f.wide else 200,
               note="横向滑动最近阅读/观看的作品，封面取自作品主视觉；点击从上次进度继续，角标显示内容类型与进度。")
    f.title("最近更新", 22)
    f.rows([
        {"label": "《长夜余火》", "icon": "auto_stories", "supporting": "漫画 · 更新至第 24 话 · 2 小时前",
         "to": "detail", "note": "打开作品详情，可查看章节目录、更新状态并开始阅读。"},
        {"label": "《山海拾遗》", "icon": "article", "supporting": "小说 · 更新至第 3 卷 第 8 章", "to": "detail"},
        {"label": "《黎明之前》", "icon": "movie", "supporting": "视频 · 更新至 EP08 · 昨天",
         "to": "player", "note": "视频条目直接进入播放器，并从上次观看时间点续播。"},
    ])


def page_library(f, t):
    f.topbar("书架", icon2="search")
    f.chips(["全部", "漫画", "小说", "视频"], checked=0)
    works = [
        {"label": "《长夜余火》", "supporting": "漫画 · 24 话 · 有更新", "icon": "auto_stories",
         "to": "detail", "note": "封面卡片进入详情；长按可批量移出书架或归类到自定义分组。"},
        {"label": "《山海拾遗》", "supporting": "小说 · 3 卷 · 已读 62%", "icon": "article", "to": "detail"},
        {"label": "《黎明之前》", "supporting": "视频 · EP08 · 已看 36%", "icon": "movie", "to": "detail"},
        {"label": "《雾中列车》", "supporting": "漫画 · 18 话 · 已读完", "icon": "auto_stories", "to": "detail"},
    ]
    f.grid(works, card_h=340 if f.wide else 250)
    if f.wide:
        f.title("最近阅读", 20)
        f.rows([
            {"label": "《长夜余火》 第 12 话", "icon": "auto_stories",
             "supporting": "继续阅读 · 昨天 22:14", "to": "reader"},
            {"label": "《黎明之前》 EP06", "icon": "movie",
             "supporting": "继续播放 · 剩 18 分钟", "to": "player"},
        ])
    else:
        f.title("共 12 部作品 · 4 部有更新", 15)


page_history = list_page(
    "历史", icon2="delete_sweep", back=False,
    tabs=["全部", "漫画", "小说", "视频"],
    sections=[
        ("今天", [
            {"label": "《长夜余火》 第 12 话", "icon": "auto_stories",
             "supporting": "漫画 · 昨天 22:14 · 已读 42%", "to": "reader",
             "note": "点击从上次阅读位置继续；历史按日期分组，可左滑删除单条或一键清空。"},
            {"label": "《黎明之前》 EP06", "icon": "movie",
             "supporting": "视频 · 20:08 · 已看 36%", "to": "player"},
            {"label": "《山海拾遗》 第 3 卷", "icon": "article",
             "supporting": "小说 · 08:30 · 已读 62%", "to": "reader"},
        ]),
        ("更早", [
            {"label": "《雾中列车》 第 07 话", "icon": "auto_stories",
             "supporting": "漫画 · 11 月 2 日", "to": "reader"},
            {"label": "《回声纪年》 EP02", "icon": "movie",
             "supporting": "视频 · 10 月 30 日", "to": "player"},
        ]),
    ],
)

page_browse = list_page(
    "浏览", icon2="search", back=False,
    tabs=["站点", "数据源", "插件订阅"],
    search="搜索站点、数据源与插件",
    sections=[
        ("已订阅数据源", [
            {"label": "漫岛", "icon": "public", "supporting": "漫画 · 12,480 部作品 · 已订阅",
             "to": "site", "note": "进入站点浏览，按最新、热门与分类查找作品；数据源由插件提供。"},
            {"label": "轻文阁", "icon": "public", "supporting": "小说 · 8,216 部作品 · 已订阅", "to": "site"},
            {"label": "影库", "icon": "public", "supporting": "视频 · 3,042 部作品 · 已订阅", "to": "site"},
        ]),
        ("插件订阅", [
            {"label": "插件仓库", "icon": "public", "supporting": "3 个已信任仓库 · 刷新索引", "to": "repositories"},
            {"label": "站点插件", "icon": "extension", "supporting": "v1.4.2 · 3 个数据源可用", "to": "extensions"},
            {"label": "管理数据源与插件", "icon": "tune", "supporting": "启用、停用、检查更新",
             "to": "sources", "note": "集中管理已订阅的数据源开关与插件版本，可跳转到插件商店安装新来源。"},
        ]),
    ],
)

page_more = list_page(
    "更多", back=False,
    sections=[
        ("内容管理", [
            {"label": "下载", "icon": "download", "supporting": "已下载 28 章 / 3 集", "to": "downloads"},
            {"label": "分类", "icon": "category", "supporting": "5 个自定义分类", "to": "categories"},
            {"label": "统计", "icon": "bar_chart", "supporting": "本月 42 小时 18 分", "to": "statistics"},
        ]),
        ("扩展与系统", [
            {"label": "插件仓库", "icon": "public", "supporting": "3 个已信任仓库 · 添加、识别、刷新", "to": "repositories",
             "note": "先添加仓库索引链接，系统识别并确认信任后，才能在插件商店按需安装。"},
            {"label": "插件商店", "icon": "extension", "supporting": "按仓库浏览 · 按需安装与更新", "to": "extensions"},
            {"label": "设置", "icon": "settings", "supporting": "外观、阅读、播放与通知", "to": "settings"},
            {"label": "数据与存储", "icon": "storage", "supporting": "已用 2.4 GB", "to": "storage"},
            {"label": "关于", "icon": "info", "supporting": "版本 0.1.0 · MIT", "to": "about"},
        ]),
    ],
)


# ---------------------------------------------------------------- 内容消费三屏

def page_detail(f, t):
    f.topbar("作品详情", back=True, icon2="more_vert",
             note="宽屏下不显示侧边导航栏，让封面与作品信息占据整幅画面；点击返回回到来源页面。")
    if f.wide:
        f.image(32, 88, 280, 392,
                note="作品封面，界面强调色从封面主色动态提取，切换作品时随封面过渡。")
        f.text(344, 112, "《长夜余火》", 32, True)
        f.text(344, 164, "漫画 · 连载中 · 每周五更新", 16)
        f.place(344, 204, [
            item("chip", "奇幻", None), item("chip", "悬疑", None),
            item("chip", "已追更", "notifications", checked=True),
        ], axis="x")
        f.text(344, 256, "简介", 20, True)
        f.text(344, 296, "永夜之城里，没有人记得上一次日出是什么时候。", 15)
        f.place(344, 352, [
            item("button", "开始阅读", "auto_stories", size=240, action=act(t("reader"), "slideUp"),
                 note="从上次阅读进度打开阅读器；漫画为竖向卷轴，小说为可调字号的分页文本。"),
            item("button", "加入书架", "bookmark_add", variant="tonal", size=240,
                 note="收藏作品并订阅更新；已收藏时按钮切换为“已在书架”。"),
        ], axis="x")
        f.text(32, 512, "目录 · 共 24 话", 20, True)
        f.place(32, 552, [
            item("listItem", "第 12 话 · 回声", "auto_stories", supporting="18 页 · 已读 42%",
                 size=1216, action=act(t("reader"), "slideUp")),
            item("listItem", "第 13 话 · 空楼", "auto_stories", supporting="21 页 · 2 天前更新",
                 size=1216, action=act(t("reader"), "slideUp")),
            item("listItem", "第 14 话 · 钟声", "auto_stories", supporting="19 页 · 最新",
                 size=1216, action=act(t("reader"), "slideUp")),
        ], axis="y")
    else:
        f.image(16, 104, 180, 250,
                note="作品封面，界面强调色从封面主色动态提取，切换作品时随封面过渡。")
        f.text(212, 112, "《长夜余火》", 22, True)
        f.text(212, 152, "漫画 · 连载中", 14)
        f.place(212, 192, item("chip", "已追更", "notifications", checked=True))
        f.place(16, 380, [item("chip", "奇幻", None), item("chip", "悬疑", None),
                          item("chip", "每周更新", None)], axis="x")
        f.text(16, 428, "简介", 20, True)
        f.text(16, 464, "永夜之城里，没有人记得上一次日出。", 14)
        f.place(16, 512, [
            item("button", "开始阅读", "auto_stories", size=182, action=act(t("reader"), "slideUp"),
                 note="从上次阅读进度打开阅读器；漫画为竖向卷轴，小说为可调字号的分页文本。"),
            item("button", "加入书架", "bookmark_add", variant="tonal", size=182,
                 note="收藏作品并订阅更新；已收藏时按钮切换为“已在书架”。"),
        ], axis="x")
        f.text(16, 584, "目录 · 共 24 话", 18, True)
        f.place(16, 624, [
            item("listItem", "第 12 话 · 回声", "auto_stories", supporting="18 页 · 已读 42%",
                 action=act(t("reader"), "slideUp")),
            item("listItem", "第 13 话 · 空楼", "auto_stories", supporting="21 页 · 2 天前更新",
                 action=act(t("reader"), "slideUp")),
            item("listItem", "第 14 话 · 钟声", "auto_stories", supporting="19 页 · 最新",
                 action=act(t("reader"), "slideUp")),
        ], axis="y")


def page_reader(f, t):
    f.topbar("第 12 话 · 长夜余火", back=True,
             note="点击正文区域可隐藏/显示上下工具栏，进入全沉浸阅读；宽屏隐藏导航并把正文限制在 720dp 居中。")
    if f.wide:
        f.box(280, 88, 720, 520, "surfaceContainerLow",
              note="正文区域：小说为可调字号/行距/背景的分页文本，漫画为竖向连续卷轴；左右滑动翻页或翻话，进度实时保存。")
        f.text(312, 128, "第十二话 · 永夜之城", 24, True)
        for y, line in [(184, "夜色像潮水一样漫过街道。"), (220, "他把灯熄灭，等第三声钟响。"),
                        (256, "窗外，有人正沿着屋檐往北走。")]:
            f.text(312, y, line, 16)
        f.place(280, 632, item("linearProgress", "", None, size=720, value=42, wavy=True,
                               note="阅读进度，随章节滚动推进；拖动可跳转章节内位置。"))
        f.toolbar(560, 688, ["skip_previous", "list", "text_fields", "skip_next"],
                  note="上一话 / 章节目录 / 阅读设置（字号、行距、翻页方向、主题）/ 下一话。")
    else:
        f.box(16, 112, 380, 520, "surfaceContainerLow",
              note="正文区域：小说为可调字号/行距/背景的分页文本，漫画为竖向连续卷轴；左右滑动翻页或翻话，进度实时保存。")
        f.text(32, 144, "第十二话 · 永夜之城", 20, True)
        for y, line in [(192, "夜色像潮水一样漫过街道。"), (224, "他把灯熄灭，等第三声钟响。"),
                        (256, "窗外，有人正沿着屋檐往北走。")]:
            f.text(32, y, line, 15)
        f.place(16, 664, item("linearProgress", "", None, size=380, value=42, wavy=True,
                              note="阅读进度，随章节滚动推进；拖动可跳转章节内位置。"))
        f.toolbar(96, 724, ["skip_previous", "list", "text_fields", "skip_next"],
                  note="上一话 / 章节目录 / 阅读设置（字号、行距、翻页方向、主题）/ 下一话。")


def page_player(f, t):
    f.topbar("黎明之前 · EP06", back=True)
    if f.wide:
        f.box(64, 88, 752, 423, "inverseSurface",
              note="视频画面区；支持全屏、倍速、投屏与弹幕开关，横屏时铺满窗口。")
        f.place(64, 527, item("linearProgress", "", None, size=752, value=36, wavy=True,
                              note="播放进度条，缓冲进度以浅色显示；拖动定位并记忆续播点。"))
        f.toolbar(336, 560, ["skip_previous", "play_arrow", "skip_next", "speed", "fullscreen"],
                  note="上一集 / 播放暂停 / 下一集 / 倍速 / 全屏。", variant="filled")
        f.text(848, 96, "选集", 20, True)
        f.place(848, 136, [
            item("listItem", "EP06 · 回声", "play_circle", supporting="32:18 · 正在播放", size=368),
            item("listItem", "EP07 · 空白来信", "movie", supporting="29:44", size=368),
            item("listItem", "EP08 · 天亮之前", "movie", supporting="31:02 · 最新", size=368),
        ], axis="y")
        f.text(64, 632, "简介", 20, True)
        f.text(64, 672, "天亮之前，城市里最后一台放映机还在转动。", 15)
    else:
        f.box(16, 104, 380, 214, "inverseSurface",
              note="视频画面区；支持全屏、倍速、投屏与弹幕开关，横屏时铺满屏幕。")
        f.place(16, 330, item("linearProgress", "", None, size=380, value=36, wavy=True,
                              note="播放进度条，缓冲进度以浅色显示；拖动定位并记忆续播点。"))
        f.toolbar(96, 366, ["skip_previous", "play_arrow", "skip_next", "speed", "fullscreen"],
                  note="上一集 / 播放暂停 / 下一集 / 倍速 / 全屏。", variant="filled")
        f.text(16, 440, "选集", 20, True)
        f.place(16, 480, [
            item("listItem", "EP06 · 回声", "play_circle", supporting="32:18 · 正在播放"),
            item("listItem", "EP07 · 空白来信", "movie", supporting="29:44"),
            item("listItem", "EP08 · 天亮之前", "movie", supporting="31:02 · 最新"),
        ], axis="y")
        f.text(16, 712, "简介", 18, True)
        f.text(16, 748, "天亮之前，最后一台放映机还在转动。", 14)


# ---------------------------------------------------------------- 浏览与来源

page_sources = list_page(
    "数据源", back=True, icon2="add",
    tabs=["已订阅", "全部来源"],
    sections=[
        ("已订阅", [
            {"label": "漫岛", "icon": "public", "supporting": "漫画 · 12,480 部作品",
             "switch": True, "checked": True, "note": "开关控制该来源是否参与全局搜索与追更检查。"},
            {"label": "轻文阁", "icon": "public", "supporting": "小说 · 8,216 部作品", "switch": True, "checked": True},
            {"label": "影库", "icon": "public", "supporting": "视频 · 3,042 部作品", "switch": True, "checked": False},
        ]),
        ("可用来源", [
            {"label": "插件提供的来源", "icon": "extension", "supporting": "安装插件后可在此启用", "to": "extensions"},
            {"label": "检查来源更新", "icon": "update", "supporting": "上次检查 2 小时前", "to": "extensions"},
            {"label": "插件仓库", "icon": "public", "supporting": "添加与管理插件来源仓库", "to": "repositories"},
        ]),
    ],
    trailing=lambda f, t: f.buttons([
        {"label": "添加数据源", "icon": "add", "variant": "tonal", "to": "extensions",
         "note": "跳转到插件商店安装来源插件，安装后回到此处启用。"},
    ]),
)


def page_site(f, t):
    f.topbar("漫岛", back=True, icon2="search")
    f.search("搜索站内作品")
    f.chips(["全部", "漫画", "小说", "视频", "最新", "热门"], checked=0)
    f.grid([
        {"label": "《长夜余火》", "supporting": "漫画 · 连载中", "icon": "auto_stories", "to": "detail"},
        {"label": "《雾中列车》", "supporting": "漫画 · 完结", "icon": "auto_stories", "to": "detail"},
        {"label": "《北街七号》", "supporting": "漫画 · 连载中", "icon": "auto_stories", "to": "detail"},
        {"label": "《白昼尽头》", "supporting": "漫画 · 完结", "icon": "auto_stories", "to": "detail"},
    ], card_h=340 if f.wide else 250)


# ---------------------------------------------------------------- 更多子页面

def downloads_tail(f, t):
    f.progress(30, note="本机存储占用 2.4 GB / 8 GB，接近上限时按最久未读自动清理。")
    f.title("批量操作", 18)
    f.buttons([
        {"label": "全部暂停", "icon": "pause", "variant": "tonal"},
        {"label": "清理已完成", "icon": "cleaning_services", "variant": "outlined"},
    ])


page_downloads = list_page(
    "下载", back=True, tabs=["已完成", "下载中"],
    sections=[("", [
        {"label": "《长夜余火》 第 12 话", "icon": "download_done", "supporting": "18 页 · 42 MB · 已完成"},
        {"label": "《黎明之前》 EP06", "icon": "download_done", "supporting": "720p · 286 MB · 已完成"},
        {"label": "《山海拾遗》 第 3 卷", "icon": "download", "supporting": "12 章 · 下载中 68%"},
        {"label": "《雾中列车》 第 08 话", "icon": "download", "supporting": "21 页 · 等待中"},
    ])],
    trailing=downloads_tail,
)

page_categories = list_page(
    "分类", back=True, icon2="add",
    sections=[("", [
        {"label": "正在追", "icon": "category", "supporting": "8 部作品", "to": "library"},
        {"label": "周末读", "icon": "category", "supporting": "5 部作品", "to": "library"},
        {"label": "已完结", "icon": "category", "supporting": "12 部作品", "to": "library"},
        {"label": "漫画", "icon": "auto_stories", "supporting": "14 部作品", "to": "library"},
        {"label": "待看片单", "icon": "movie", "supporting": "6 部作品", "to": "library"},
    ])],
    trailing=lambda f, t: f.buttons([
        {"label": "新建分类", "icon": "add", "variant": "tonal",
         "note": "创建后可给作品打多个分类标签，书架按分类筛选或分组显示。"},
    ]),
)


def page_statistics(f, t):
    f.topbar("统计", back=True)
    f.title("本月概览", 20)
    f.grid([
        {"label": "42 小时 18 分", "supporting": "总阅读 / 观看时长", "icon": "schedule"},
        {"label": "24 话", "supporting": "漫画", "icon": "auto_stories"},
        {"label": "12 章", "supporting": "小说", "icon": "article"},
        {"label": "8 集", "supporting": "视频", "icon": "movie"},
    ], card_h=140 if f.wide else 120, no_image=True)
    f.title("内容分布", 18)
    f.rows([
        {"label": "漫画", "icon": "auto_stories", "supporting": "占比 52% · 22 小时", "to": "library"},
        {"label": "小说", "icon": "article", "supporting": "占比 27% · 11 小时", "to": "library"},
        {"label": "视频", "icon": "movie", "supporting": "占比 21% · 9 小时", "to": "library"},
    ])


page_repositories = list_page(
    "插件仓库", back=True, icon2="add",
    sections=[
        ("已添加的仓库", [
            {"label": "社区漫画源仓库", "icon": "public",
             "supporting": "42 个插件 · 指纹 8f3a2c1d · 已启用",
             "switch": True, "checked": True,
             "note": "应用不内置任何仓库。用户粘贴仓库索引链接，系统识别（名称、插件数、身份指纹）后由用户确认信任才生效。"},
            {"label": "小说源合集", "icon": "public",
             "supporting": "18 个插件 · 身份指纹 c91b7e04 · 已启用", "switch": True, "checked": True},
            {"label": "实验性视频源", "icon": "public",
             "supporting": "6 个插件 · 上次刷新失败 · 已停用", "switch": True, "checked": False},
        ]),
        ("仓库操作", [
            {"label": "添加仓库", "icon": "add",
             "supporting": "粘贴 HTTPS 索引链接 → 识别 → 信任并添加",
             "note": "识别阶段不落库；确认页展示仓库信息、插件数与身份指纹，并提示第三方代码风险。"},
            {"label": "刷新全部仓库", "icon": "update", "supporting": "重新拉取并校验索引；失败保留上次缓存"},
            {"label": "移除仓库", "icon": "delete_sweep",
             "supporting": "提示该仓库下已安装插件数，可选仅移除或同时卸载"},
        ]),
        ("安全提示", [
            {"label": "第三方代码与身份指纹信任", "icon": "info",
             "supporting": "仓库 ID、链接或发布者公钥变化需重新确认；插件只能从来源仓库更新"},
        ]),
    ],
    trailing=lambda f, t: f.buttons([
        {"label": "前往插件商店", "icon": "extension", "variant": "tonal", "to": "extensions",
         "note": "仓库确认后，在插件商店按仓库分组浏览，逐个按需安装。"},
    ]),
)

page_extensions = list_page(
    "插件商店", back=True, icon2="update",
    search="搜索仓库内的插件", tabs=["可安装", "有更新", "已安装"],
    sections=[
        ("社区漫画源仓库", [
            {"label": "漫画源扩展", "icon": "extension", "supporting": "v2.1.0 · 漫画 · 可安装", "to": "sources",
             "note": "安装前展示申请域名、媒体类型与能力；确认后从该仓库下载 ZIP，校验整包哈希再安装。"},
            {"label": "视频源扩展", "icon": "extension", "supporting": "v1.2.1 · 视频 · 可安装", "to": "sources"},
            {"label": "元数据插件", "icon": "extension", "supporting": "v0.6.0 · 实验性 · 需新版应用", "to": "sources"},
        ]),
        ("小说源合集", [
            {"label": "轻小说源", "icon": "extension", "supporting": "v0.9.3 · 小说 · 可安装", "to": "sources"},
            {"label": "站点插件", "icon": "extension", "supporting": "v1.4.2 → v1.5.0 · 有更新", "to": "sources"},
        ]),
        ("已安装", [
            {"label": "站点插件", "icon": "extension", "supporting": "v1.4.2 · 来自社区漫画源仓库", "to": "sources"},
        ]),
        ("来源管理", [
            {"label": "管理插件仓库", "icon": "public",
             "supporting": "添加仓库、识别信任、刷新索引与移除", "to": "repositories"},
            {"label": "数据源管理", "icon": "tune", "supporting": "安装后回到此处启用", "to": "sources"},
        ]),
    ],
)

page_settings = list_page(
    "设置", back=True,
    sections=[
        ("外观", [
            {"label": "深色模式", "icon": "dark_mode", "supporting": "跟随系统", "switch": True, "checked": True},
            {"label": "封面动态取色", "icon": "palette", "supporting": "强调色取自当前作品封面",
             "switch": True, "checked": True},
            {"label": "界面语言", "icon": "translate", "supporting": "简体中文"},
        ]),
        ("阅读与播放", [
            {"label": "默认阅读方向", "icon": "swap_vert", "supporting": "竖向卷轴 / 左右翻页"},
            {"label": "自动续播", "icon": "play_circle", "supporting": "从上次时间点继续",
             "switch": True, "checked": True},
            {"label": "默认画质", "icon": "tune", "supporting": "跟随网络自动选择"},
        ]),
        ("通知", [
            {"label": "追更提醒", "icon": "notifications", "supporting": "订阅作品更新时通知",
             "switch": True, "checked": True},
        ]),
    ],
)


def page_about(f, t):
    f.topbar("关于", back=True)
    f.title("内容库", 28)
    f.title("版本 0.1.0 · MIT 开源", 14)
    f.panel("一个书架，三种内容", "漫画、小说与视频共用追更、收藏与进度；支持插件数据源、离线下载与本地备份。",
            h=120 if f.wide else 110)
    f.title("项目", 18)
    f.rows([
        {"label": "开源许可", "icon": "gavel", "supporting": "MIT · 第三方组件致谢", "to": "settings"},
        {"label": "更新日志", "icon": "description", "supporting": "查看版本变更", "to": "settings"},
        {"label": "反馈问题", "icon": "bug_report", "supporting": "提交问题与建议", "to": "settings"},
        {"label": "数据与存储", "icon": "storage", "supporting": "备份与清理", "to": "storage"},
    ])
    f.title("产品名称待定，此处为中性占位名", 13)


def storage_tail(f, t):
    f.progress(30, note="已用 2.4 GB / 8 GB；缓存与下载分开统计。")
    f.buttons([
        {"label": "清除缓存", "icon": "cleaning_services", "variant": "outlined",
         "note": "仅清除图片与临时缓存，不影响已下载内容和阅读进度。"},
    ])


page_storage = list_page(
    "数据与存储", back=True,
    sections=[
        ("存储", [
            {"label": "图片缓存", "icon": "image", "supporting": "680 MB · 自动清理", "switch": True, "checked": True},
            {"label": "下载位置", "icon": "folder", "supporting": "内部存储 / ContentLibrary"},
            {"label": "自动清理", "icon": "cleaning_services", "supporting": "超过 8 GB 时清理最久未读",
             "switch": True, "checked": False},
        ]),
        ("备份", [
            {"label": "自动备份", "icon": "backup", "supporting": "每天一次 · 保留最近 7 份",
             "switch": True, "checked": True},
            {"label": "创建备份", "icon": "cloud_upload", "supporting": "包含书架、分类、进度与设置",
             "to": "settings"},
            {"label": "恢复备份", "icon": "settings_backup_restore", "supporting": "从本地文件导入", "to": "settings"},
        ]),
    ],
    trailing=storage_tail,
)


# ---------------------------------------------------------------- 屏幕清单

SCREENS = [
    dict(key="home", name="首页", group="主导航", sel=0, nav_phone=True,
         note="首页：按内容类型切换的推荐流，含继续阅读横向卡片与最近更新列表。", build=page_home),
    dict(key="library", name="书架", group="主导航", sel=1, nav_phone=True,
         note="书架：统一收藏漫画、小说与视频，封面网格 + 类型筛选 + 继续阅读记录。", build=page_library),
    dict(key="history", name="历史", group="主导航", sel=2, nav_phone=True,
         note="历史：按日期分组的阅读与观看记录，可从任意条目续播或续读。", build=page_history),
    dict(key="browse", name="浏览", group="主导航", sel=3, nav_phone=True,
         note="浏览：站点、数据源与插件订阅三个分页，进入站点浏览与插件商店。", build=page_browse),
    dict(key="more", name="更多", group="主导航", sel=4, nav_phone=True,
         note="更多：内容管理与系统设置的路由入口，覆盖下载、分类、统计、插件仓库、插件商店、设置、关于、数据与存储。", build=page_more),

    dict(key="detail", name="作品详情", group="内容消费", sel=None, nav_phone=False,
         note="作品详情：封面驱动的强调色、类型标签、简介、开始阅读/加入书架与章节目录；宽屏去掉侧边导航栏以更沉浸。", build=page_detail),
    dict(key="reader", name="阅读器", group="内容消费", sel=None, nav_phone=False,
         bg="surfaceContainerLow", note="阅读器：小说分页文本与漫画竖向卷轴共用一套沉浸式界面，隐藏全部导航，进度自动保存。", build=page_reader),
    dict(key="player", name="播放器", group="内容消费", sel=None, nav_phone=False,
         bg="surfaceContainerLow", note="播放器：视频画面、波浪进度条、播放控制与选集列表，从上次观看点续播。", build=page_player),

    dict(key="sources", name="数据源管理", group="浏览与来源", sel=3, nav_phone=False,
         note="数据源管理：已订阅来源的开关、可用来源列表与添加入口，插件提供来源。", build=page_sources),
    dict(key="site", name="站点浏览", group="浏览与来源", sel=3, nav_phone=False,
         note="站点浏览：某个数据源内的搜索、类型/排序筛选与作品封面网格。", build=page_site),

    dict(key="downloads", name="下载", group="更多 · 子页面", sel=4, nav_phone=False,
         note="下载：已下载与下载中的任务列表、存储占用与批量暂停/清理。", build=page_downloads),
    dict(key="categories", name="分类", group="更多 · 子页面", sel=4, nav_phone=False,
         note="分类：自定义分类列表与新建入口，点击分类回到带筛选的书架。", build=page_categories),
    dict(key="statistics", name="统计", group="更多 · 子页面", sel=4, nav_phone=False,
         note="统计：本月时长、各内容类型数量与占比分布。", build=page_statistics),
    dict(key="repositories", name="插件仓库", group="更多 · 子页面", sel=4, nav_phone=False,
         note="插件仓库：添加仓库索引链接，系统识别并展示名称、插件数与指纹，用户确认信任后生效；可刷新、停用与移除，删除时提示影响范围。", build=page_repositories),
    dict(key="extensions", name="插件商店", group="更多 · 子页面", sel=4, nav_phone=False,
         note="插件商店：按已信任仓库分组浏览插件，可安装 / 有更新 / 已安装三分面，逐个按需安装并展示权限，安装后回到数据源管理启用。", build=page_extensions),
    dict(key="settings", name="设置", group="更多 · 子页面", sel=4, nav_phone=False,
         note="设置：外观、阅读与播放、通知三组开关与选项。", build=page_settings),
    dict(key="about", name="关于", group="更多 · 子页面", sel=4, nav_phone=False,
         note="关于：版本、项目说明、许可与反馈入口。", build=page_about),
    dict(key="storage", name="数据与存储", group="更多 · 子页面", sel=4, nav_phone=False,
         note="数据与存储：缓存与下载位置、自动清理、自动备份、创建与恢复备份。", build=page_storage),
]


def frame_pos(slot: int, wide: bool):
    """每个逻辑屏占一个 1360×2000 槽位：手机在上、宽屏在下，成对对齐。"""
    row, col = divmod(slot, 4)
    return col * 1360, row * 2000 + (1000 if wide else 0)


def build_doc() -> dict:
    doc = {
        "title": "内容库",
        "brief": "一个把漫画、小说和视频放进同一个书架的内容应用。底部导航为首页、书架、历史、浏览、更多；浏览管理数据源与插件订阅，更多通往下载、分类、统计、插件仓库、插件商店、设置、关于、数据与存储。插件采用仓库优先流程：先添加仓库索引链接并由系统识别、用户确认信任，再在插件商店按仓库分组按需安装与更新；应用不内置、不推荐任何正式仓库。三种内容共用追更、收藏与阅读进度；深色优先，强调色取自作品封面。宽屏下仅 5 个主导航 tab 屏显示侧边导航栏，其余子页面隐藏侧栏、用顶栏返回按钮保持沉浸。",
        "frame": "phone",
        "platform": "web",
        "paletteKey": "purple",
        "dynamicColor": True,
        "theme": {
            "dark": True, "bothModes": True, "contrast": "standard",
            "shape": "rounded", "font": "roboto", "emphasized": True, "motion": "expressive",
        },
        "frames": [],
        "groups": [],
    }
    for slot, spec in enumerate(SCREENS):
        for wide in (False, True):
            x, y = frame_pos(slot, wide)
            w, h = (1280, 800) if wide else (412, 892)
            t = resolver(wide)
            s = Screen(doc, spec["key"] + ("_w" if wide else ""), spec["name"],
                       x, y, w, h, spec["note"], spec.get("bg"))
            # 宽屏侧边导航栏只属于 5 个主导航 tab 屏；其余所有子页面一律沉浸，靠顶栏返回
            nav_wide = spec["key"] in MAIN
            f = Flow(s, t, wide, nav_phone=spec["nav_phone"], nav_wide=nav_wide,
                     selected=spec["sel"])
            spec["build"](f, t)
    return doc


def share_link(doc: dict) -> str:
    raw = json.dumps(doc, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    c = zlib.compressobj(9, zlib.DEFLATED, -15)
    packed = c.compress(raw) + c.flush()
    return BASE + "#docz=" + base64.urlsafe_b64encode(packed).decode().rstrip("=")


def main():
    doc = build_doc()
    link = share_link(doc)
    (ROOT / "design.json").write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (ROOT / "share-link.txt").write_text(link + "\n", encoding="utf-8")
    (ROOT / "prototype.html").write_text(html_page(link, doc), encoding="utf-8")
    print(f"frames={len(doc['frames'])} groups={len(doc['groups'])} "
          f"items={sum(len(g['items']) for g in doc['groups'])} link_len={len(link)}")


def html_page(link: str, doc: dict) -> str:
    grouped = {}
    for spec in SCREENS:
        grouped.setdefault(spec["group"], []).append((spec["name"], spec["note"]))
    blocks = []
    for g, entries in grouped.items():
        lis = "".join(
            f"<li><strong>{n}</strong><span>{note.split('：', 1)[-1]}</span></li>" for n, note in entries
        )
        blocks.append(f"<h3>{g}</h3><ul class='screens'>{lis}</ul>")
    screen_html = "".join(blocks)
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>内容库 · 设计原型</title>
<style>
  :root {{ color-scheme: light; --primary:#6750A4; --on-primary:#fff; --surface:#fff;
    --bg:#F7F2FA; --ink:#1D1B20; --muted:#625B71; --line:#E7E0EC; --container:#F3EDF7; }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--ink);
    font:15px/1.7 -apple-system,BlinkMacSystemFont,"Noto Sans SC","PingFang SC",sans-serif; }}
  main {{ max-width:900px; margin:0 auto; padding:48px 24px 72px; }}
  .eyebrow {{ color:var(--primary); font-weight:700; letter-spacing:.08em; font-size:12px; text-transform:uppercase; }}
  h1 {{ font-size:34px; line-height:1.25; margin:10px 0 8px; letter-spacing:-.02em; }}
  h3 {{ font-size:13px; color:var(--muted); font-weight:700; margin:22px 0 6px; letter-spacing:.04em; }}
  h3:first-child {{ margin-top:0; }}
  .lede {{ color:var(--muted); margin:0 0 30px; font-size:16px; }}
  .card {{ background:var(--surface); border:1px solid var(--line); border-radius:24px;
    padding:28px; margin-bottom:20px; box-shadow:0 1px 2px rgba(0,0,0,.03); }}
  h2 {{ font-size:18px; margin:0 0 14px; }}
  .actions {{ display:flex; flex-wrap:wrap; gap:12px; margin:0 0 18px; }}
  a.btn, button.btn {{ display:inline-flex; align-items:center; gap:8px; border:0; cursor:pointer;
    background:var(--primary); color:var(--on-primary); font:600 15px/1 inherit;
    padding:15px 24px; border-radius:999px; text-decoration:none; }}
  a.btn:hover, button.btn:hover {{ filter:brightness(1.08); }}
  button.btn.secondary {{ background:var(--container); color:var(--primary); }}
  textarea {{ width:100%; min-height:96px; resize:vertical; padding:14px 16px;
    font:12px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace;
    color:var(--muted); background:#FBF9FD; border:1px solid var(--line); border-radius:14px; }}
  .hint {{ color:var(--muted); font-size:13px; margin:10px 0 0; }}
  ul.screens {{ list-style:none; padding:0; margin:0; }}
  ul.screens li {{ display:flex; gap:16px; padding:9px 0; border-top:1px solid var(--line); }}
  ul.screens li:first-child {{ border-top:0; }}
  ul.screens strong {{ min-width:112px; color:var(--primary); font-weight:600; }}
  ul.screens span {{ color:var(--muted); font-size:14px; }}
  .meta {{ display:flex; flex-wrap:wrap; gap:8px; margin:0 0 18px; }}
  .tag {{ background:var(--container); color:var(--primary); border-radius:999px;
    padding:5px 12px; font-size:12px; font-weight:600; }}
  ol {{ padding-left:20px; color:var(--muted); margin:0; }}
  ol li {{ padding:4px 0; }}
  code {{ background:var(--container); padding:2px 6px; border-radius:6px;
    font-size:13px; color:var(--primary); }}
  footer {{ color:var(--muted); font-size:13px; margin-top:26px; }}
</style>
</head>
<body>
<main>
  <div class="eyebrow">M3E Canvas · 设计原型</div>
  <h1>内容库</h1>
  <p class="lede">视频 · 小说 · 漫画三合一内容应用。视觉参考 Komikku：Material 3 深色优先、封面驱动配色、书架与追更、插件数据源与沉浸式阅读器。产品名称待定，画布标题目前为中性占位名。</p>

  <div class="card">
    <h2>打开原型</h2>
    <div class="meta">
      <span class="tag">5 个导航 tab</span>
      <span class="tag">18 个逻辑屏</span>
      <span class="tag">手机 412×892 + 桌面 1280×800</span>
      <span class="tag">宽屏仅主屏显示侧栏</span>
      <span class="tag">深色 / 浅色双模式</span>
      <span class="tag">M3 Expressive 动效</span>
    </div>
    <div class="actions">
      <a class="btn" href="{link}" target="_blank" rel="noopener">在 M3E Canvas 打开 →</a>
      <button class="btn secondary" id="copy">复制分享链接</button>
    </div>
    <textarea id="link" readonly spellcheck="false">{link}</textarea>
    <p class="hint">链接把整份设计编码在 URL 片段里，不经过服务器、不需要账号。若链接太长不好复制，也可以下载 design.json，在画布里用 <code>Open project</code> 打开。</p>
  </div>

  <div class="card">
    <h2>屏幕与流程</h2>
    {screen_html}
    <p class="hint">导航顺序为 首页 · 书架 · 历史 · 浏览 · 更多，五个主屏互相可跳转；「浏览」通往数据源管理与站点浏览，「更多」通往 8 个子页面（含插件仓库与插件商店）。宽屏只有 5 个主导航 tab 屏显示侧边导航栏；其余 13 个子页面（详情、阅读器、播放器，以及浏览与更多下的全部二级页）一律隐藏侧栏，改用顶栏返回按钮保持沉浸。详情宽屏改为整幅画面，封面在左、信息在右、目录在下。每个逻辑屏在画布上成对上下排列：手机版在上、宽屏版在下，同名屏幕会被识别为「同一个屏幕的两种宽度」。</p>
  </div>

  <div class="card">
    <h2>打开之后做什么</h2>
    <ol>
      <li>点上方按钮打开画布，按 <code>Tidy</code> 让栏贴边、相邻组件吸附成组。</li>
      <li>按 <code>P</code> 进入预览，点列表、封面、章节、导航项与「更多」里的子页面走一遍流程。</li>
      <li>在主题面板里调配色、圆角、字体；<code>bothModes</code> 已开启，可切浅色/深色看两套效果。</li>
      <li>点 <code>Copy prompt</code> 把整个设计导出成给编码工具的提示词；也能把单屏存成 PNG。</li>
      <li>要改设计：编辑 <code>build_prototype.py</code> 里的屏幕定义后重跑，会生成新链接与新的 design.json。</li>
    </ol>
  </div>

  <footer>基于 lnkiai/m3e-canvas（MIT）的文档格式生成 · 目标平台标注为 web · 封面使用占位图区，未外链图片</footer>
</main>
<script>
  const box = document.getElementById('link');
  document.getElementById('copy').addEventListener('click', async () => {{
    try {{ await navigator.clipboard.writeText(box.value); }}
    catch (e) {{ box.select(); document.execCommand('copy'); }}
    const b = document.getElementById('copy');
    b.textContent = '已复制 ✓';
    setTimeout(() => b.textContent = '复制分享链接', 1800);
  }});
</script>
</body>
</html>
"""


if __name__ == "__main__":
    main()
