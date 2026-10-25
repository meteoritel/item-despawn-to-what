// GUI 探针的客户端原语集合：每次 eval 由驱动脚本前置注入，供 case 步骤调用。
// 只做「取得真实状态 / 走真实输入路径 / 截图 / 导出布局快照」，不含任何断言与期望值。
var __gp = (function () {
  var MC = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  var SS = Java.loadClass('net.minecraft.client.Screenshot');
  var File = Java.loadClass('java.io.File');
  var CH = Java.loadClass('net.neoforged.neoforge.client.ClientHooks');
  var GLFW = Java.loadClass('org.lwjgl.glfw.GLFW');
  var Integer = Java.loadClass('java.lang.Integer');
  var Workspace = Java.loadClass('com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace');
  var DIR = __gpRunDir;

  function join(name) {
    return DIR + '/' + name;
  }

  function windowInfo() {
    var win = MC.getWindow();
    var rt = MC.getMainRenderTarget();
    return {
      winWidth: win.getWidth(), winHeight: win.getHeight(),
      fbWidth: rt.width, fbHeight: rt.height,
      scale: win.getGuiScale(),
      logicalWidth: win.getGuiScaledWidth(), logicalHeight: win.getGuiScaledHeight()
    };
  }

  // 窗口尺寸与 GUI 缩放一起改，缩放档位决定逻辑坐标，窗口尺寸决定帧缓冲；两者都要改才能命中目标逻辑分辨率。
  function resize(width, height, scale) {
    GLFW.glfwSetWindowSize(MC.getWindow().getWindow(), width, height);
    MC.options.guiScale().set(Integer.valueOf(scale));
    MC.resizeDisplay();
    return windowInfo();
  }

  function shot(name) {
    var file = new File(join(name));
    file.getParentFile().mkdirs();
    var img = SS.takeScreenshot(MC.getMainRenderTarget());
    try {
      img.writeToFile(file);
      return { path: join(name), width: img.getWidth(), height: img.getHeight() };
    } finally {
      img.close();
    }
  }

  function click(x, y, button) {
    button = button === undefined ? 0 : button;
    var s = MC.screen;
    if (s === null) return { handled: false, reason: 'no-screen' };
    var pre = CH.onScreenMouseClickedPre(s, x, y, button);
    var handled = s.mouseClicked(x, y, button);
    CH.onScreenMouseClickedPost(s, x, y, button, handled);
    s.mouseReleased(x, y, button);
    return { handled: Boolean(handled), pre: Boolean(pre), screen: String(MC.screen) };
  }

  // 走真实输入路径的一步拖动：按下、逐帧移动、松开；引擎只在按住时发 dragged，所以中间帧要自己补。
  function drag(x0, y0, x1, y1, steps, button) {
    steps = steps === undefined ? 4 : steps;
    button = button === undefined ? 0 : button;
    var s = MC.screen;
    if (s === null) return { handled: false, reason: 'no-screen' };
    CH.onScreenMouseClickedPre(s, x0, y0, button);
    var handled = s.mouseClicked(x0, y0, button);
    CH.onScreenMouseClickedPost(s, x0, y0, button, handled);
    for (var i = 1; i <= steps; i++) {
      var x = x0 + (x1 - x0) * i / steps;
      var y = y0 + (y1 - y0) * i / steps;
      s.mouseDragged(x, y, button, 0, 0);
    }
    s.mouseReleased(x1, y1, button);
    return { handled: Boolean(handled) };
  }

  function wheel(x, y, delta) {
    var s = MC.screen;
    if (s === null) return { handled: false, reason: 'no-screen' };
    var handled = s.mouseScrolled(x, y, 0, delta);
    return { handled: Boolean(handled) };
  }

  function key(code, scan, mods) {
    var s = MC.screen;
    if (s === null) return { handled: false, reason: 'no-screen' };
    var handled = s.keyPressed(code, scan === undefined ? 0 : scan, mods === undefined ? 0 : mods);
    return { handled: Boolean(handled) };
  }

  function typeText(text) {
    var s = MC.screen;
    if (s === null) return { handled: false, reason: 'no-screen' };
    var any = false;
    for (var i = 0; i < text.length; i++) {
      if (s.charTyped(text.charAt(i), 0)) any = true;
    }
    return { handled: any };
  }

  function widgets() {
    var s = MC.screen;
    if (s === null) return [];
    var out = [];
    var list = s.children();
    for (var i = 0; i < list.size(); i++) {
      var w = list.get(i);
      var item = { index: i, type: String(w).split('@')[0].split('.').pop() };
      try { item.x = w.getX(); item.y = w.getY(); item.width = w.getWidth(); item.height = w.getHeight(); } catch (e) { }
      try { item.label = String(w.getMessage().getString()); } catch (e2) { }
      out.push(item);
    }
    return out;
  }

  // 自绘界面的按钮不在原版控件树里，只存在于排版快照；原版界面则只有 children()。两处都找。
  function findClickable(label) {
    var nodes = flat(layout());
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (n.text === label && (n.kind === 'CONTROL' || n.kind === 'TEXT_INPUT' || n.kind === 'ROW')) return n;
    }
    var list = widgets();
    for (var j = 0; j < list.length; j++) {
      if (list[j].label === label) return list[j];
    }
    return null;
  }

  function clickWidget(label) {
    var w = findClickable(label);
    if (w === null) return { handled: false, reason: 'widget-not-found:' + label, widgets: widgets() };
    return click(w.x + w.width / 2, w.y + w.height / 2, 0);
  }

  // 客户端语言可能是中文或英文，导航按钮按候选文案依次尝试，避免把语言绑进脚本。
  function clickWidgetAny(labels) {
    for (var i = 0; i < labels.length; i++) {
      var w = findClickable(labels[i]);
      if (w !== null) return click(w.x + w.width / 2, w.y + w.height / 2, 0);
    }
    return { handled: false, reason: 'widget-not-found:' + labels.join('|'), widgets: widgets() };
  }

  function screenName() {
    var s = MC.screen;
    return s === null ? 'null' : String(s).split('@')[0].split('.').pop();
  }

  // 一步菜单导航：每次调用只推进一个界面，驱动脚本负责等待渲染，让每个界面都真实落地过。
  // 客户端里“在世界中”的判据是 level 已装载；AutoJS.where() 在开着屏幕时口径不同，不作判据。
  function inWorld() {
    return MC.level !== null && MC.player !== null;
  }

  function menuStep(worldName) {
    var screen = screenName();
    if (inWorld()) return { done: true, screen: screen };
    if (screen === 'TitleScreen') {
      return { done: false, action: 'singleplayer', result: clickWidgetAny(['Singleplayer', '单人游戏']) };
    }
    if (screen === 'SelectWorldScreen') {
      var s = MC.screen;
      var list = null;
      var children = s.children();
      for (var i = 0; i < children.size(); i++) {
        if (String(children.get(i)).indexOf('WorldSelectionList') >= 0) list = children.get(i);
      }
      if (list === null) return { done: false, action: 'no-list' };
      if (list.getSelected() === null) {
        var entries = list.children();
        var chosenIndex = -1;
        for (var k = 0; k < entries.size(); k++) {
          var name = '';
          try { name = String(entries.get(k).getLevelName()); } catch (e) { }
          if (worldName === undefined || name === worldName) { chosenIndex = k; break; }
        }
        if (chosenIndex < 0) return { done: false, action: 'no-world', entries: entries.size() };
        // 行的坐标是列表内部几何（getRowTop 为 protected），选中态走列表公开的 setSelected。
        list.setSelected(entries.get(chosenIndex));
        return { done: false, action: 'select-world', row: chosenIndex };
      }
      return { done: false, action: 'play-world', result: clickWidgetAny(['Play Selected World', '进入选中的世界']) };
    }
    return { done: false, action: 'wait', screen: screen };
  }

  // 把界面推到编辑器打开：发真实命令，服务端取锁成功才会下发打开包。
  function editorStep() {
    var screen = screenName();
    if (screen === 'RuleEditorScreen') return { done: true, screen: screen };
    if (!inWorld()) return { done: false, action: 'not-in-world', screen: screen };
    var state = String(Workspace.instance().state());
    if (state === 'FREE') {
      openEditor();
      return { done: false, action: 'command-sent', screen: screen };
    }
    return { done: false, action: 'waiting-state', screen: screen, state: state };
  }

  function convNodes(list) {
    var out = [];
    for (var i = 0; i < list.size(); i++) {
      var n = list.get(i);
      var rect = n.rect();
      var kids = n.children();
      out.push({
        kind: String(n.kind()), x: rect.x(), y: rect.y(), width: rect.width(), height: rect.height(),
        text: String(n.text().getString()), textWidth: n.textWidth(), textBudget: n.textBudget(),
        enabled: n.enabled(), visible: n.visible(), clipped: n.clipped(),
        children: convNodes(kids)
      });
    }
    return out;
  }

  // 布局快照由 kit 的只读接口给出；自绘 UI 没有原版控件树，这里是唯一可信的几何来源。
  function layout() {
    var s = MC.screen;
    if (s === null) return [];
    try {
      return convNodes(s.layoutNodes());
    } catch (e) {
      return [{ kind: 'ERROR', x: 0, y: 0, width: 0, height: 0, text: 'layoutNodes missing: ' + e, textWidth: 0, textBudget: 0, enabled: false, visible: false, clipped: false, children: [] }];
    }
  }

  function rects(kinds) {
    var wanted = kinds === undefined ? null : kinds;
    var nodes = flat(layout());
    var out = [];
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (n.kind === 'ERROR') continue;
      if (n.width <= 0 || n.height <= 0) continue;
      if (wanted !== null && wanted.indexOf(n.kind) < 0) continue;
      out.push({ x: n.x, y: n.y, w: n.width, h: n.height, label: n.text });
    }
    return out;
  }

  function textNodes() {
    var nodes = flat(layout());
    var out = [];
    for (var i = 0; i < nodes.length; i++) {
      if (nodes[i].text) out.push(nodes[i].text);
    }
    return out;
  }

  function flat(nodes) {
    var out = [];
    for (var i = 0; i < nodes.length; i++) {
      out.push(nodes[i]);
      var kids = flat(nodes[i].children);
      for (var k = 0; k < kids.length; k++) out.push(kids[k]);
    }
    return out;
  }

  function screenInfo() {
    var s = MC.screen;
    if (s === null) return { screen: 'null', title: '' };
    var title = '';
    try { title = String(s.getTitle().getString()); } catch (e) { }
    return { screen: String(s).split('@')[0].split('.').pop(), className: String(s.getClass ? s.getClass().getName() : s), title: title };
  }

  function state() {
    var s = MC.screen;
    var out = {
      where: String(AutoJS.where()),
      screen: s === null ? 'null' : String(s).split('@')[0].split('.').pop(),
      player: MC.player === null ? 'null' : String(MC.player.getName().getString()),
      connected: MC.getConnection() !== null,
      workspace: String(Workspace.instance().state())
    };
    var server = MC.getSingleplayerServer();
    out.singleplayerServer = server === null ? 'null' : String(server.isRunning());
    return out;
  }

  // 客户端工作区是运行时单例；这里只读它的公开状态，任何一项取不到都不影响其它项。
  function workspace() {
    var ws = Workspace.instance();
    var out = {};
    try { out.state = String(ws.state()); } catch (e) { out.state = 'UNKNOWN'; }
    try { out.sessionId = String(ws.sessionId()); } catch (e) { out.sessionId = null; }
    try { out.resultSeq = ws.resultSeq(); } catch (e) { out.resultSeq = null; }
    try {
      var snap = ws.snapshot();
      out.entries = snap === null ? -1 : snap.entries().size();
    } catch (e) {
      out.entries = -1;
    }
    return out;
  }

  function openEditor() {
    MC.player.connection.sendCommand('idtw config edit');
    return { sent: true };
  }

  function dumpJson(name, value) {
    // Rhino 上下文里没有 java 包全局，落盘走 java.nio 的显式类引用。
    var Files = Java.loadClass('java.nio.file.Files');
    var Paths = Java.loadClass('java.nio.file.Paths');
    var path = Paths.get(join(name));
    Files.createDirectories(path.getParent());
    Files.writeString(path, JSON.stringify(value));
    return { path: join(name), bytes: Files.size(path) };
  }

  return {
    mc: MC,
    window: windowInfo,
    resize: resize,
    shot: shot,
    click: click,
    drag: drag,
    wheel: wheel,
    key: key,
    typeText: typeText,
    widgets: widgets,
    findClickable: findClickable,
    clickWidget: clickWidget,
    clickWidgetAny: clickWidgetAny,
    screenName: screenName,
    menuStep: menuStep,
    editorStep: editorStep,
    layout: layout,
    flat: flat,
    rects: rects,
    textNodes: textNodes,
    screenInfo: screenInfo,
    state: state,
    workspace: workspace,
    openEditor: openEditor,
    dumpJson: dumpJson
  };
})();
