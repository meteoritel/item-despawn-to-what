// GUI 探针的客户端原语集合：由驱动在会话内安装一次，之后每次 eval 只发表达式本身。
// 只做「取得真实状态 / 走真实输入路径 / 截图 / 导出布局快照」，不含任何断言与期望值。
// 版本号与 run.py 的 PRELUDE_VERSION 一一对应：驱动发现会话里装的是旧版本会重装，
// 复用同一个客户端会话换 RunDir 时才能同时换掉证据目录与新增原语。
var __gp = (function () {
  var VERSION = 9;
  var MC = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  var SS = Java.loadClass('net.minecraft.client.Screenshot');
  var File = Java.loadClass('java.io.File');
  var CH = Java.loadClass('net.neoforged.neoforge.client.ClientHooks');
  var GLFW = Java.loadClass('org.lwjgl.glfw.GLFW');
  var Integer = Java.loadClass('java.lang.Integer');
  var NativeImage = Java.loadClass('com.mojang.blaze3d.platform.NativeImage');
  var Workspace = Java.loadClass('com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace');
  var Language = Java.loadClass('net.minecraft.locale.Language');

  // 证据目录由驱动以 var __gpRunDir 注入；每次调用时现读，保证同一个会话换 RunDir 后路径跟着走。
  function join(name) {
    return String(__gpRunDir) + '/' + name;
  }

  // 本次求值内的布局缓存：begin() 清空，同一 eval 里多次读布局只构建一次。
  var cachedLayout = null;
  var cachedFlat = null;
  var beganAt = 0;

  function begin() {
    beganAt = Date.now();
    cachedLayout = null;
    cachedFlat = null;
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

  function stemOf(name) {
    var text = String(name);
    return text.substring(text.length - 4).toLowerCase() === '.png'
      ? text.substring(0, text.length - 4) : text;
  }

  // 截图：写全尺寸 PNG 与逻辑分辨率缩略图，一次求值返回元数据。
  // takeScreenshot 取的是上一帧已完成的帧缓冲，调用方要在动作与截图之间隔开至少两帧。
  function shot(name) {
    var started = Date.now();
    var scale = windowInfo().scale;
    if (!scale || scale < 1) scale = 1;
    var file = new File(join(name));
    file.getParentFile().mkdirs();
    var img = SS.takeScreenshot(MC.getMainRenderTarget());
    var thumb = null;
    try {
      img.writeToFile(file);
      var fullWidth = img.getWidth();
      var fullHeight = img.getHeight();
      var thumbWidth = Math.max(1, Math.round(fullWidth / scale));
      var thumbHeight = Math.max(1, Math.round(fullHeight / scale));
      thumb = new NativeImage(thumbWidth, thumbHeight, false);
      img.resizeSubRectTo(0, 0, fullWidth, fullHeight, thumb);
      var thumbName = stemOf(name) + '-thumb.png';
      thumb.writeToFile(new File(join(thumbName)));
      return {
        path: join(name), thumb: join(thumbName),
        width: fullWidth, height: fullHeight, scale: scale,
        rects: null, shot_ms: Date.now() - started
      };
    } finally {
      if (thumb !== null) thumb.close();
      img.close();
    }
  }

  function click(x, y, button) {
    button = button === undefined ? 0 : button;
    var s = MC.screen;
    if (s === null) return { handled: false, reason: 'no-screen' };
    var pre = CH.onScreenMouseClickedPre(s, x, y, button);
    var handled = pre || s.mouseClicked(x, y, button);
    if (!pre) CH.onScreenMouseClickedPost(s, x, y, button, handled);
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
    if (s === null) {
      var Bus = Java.loadClass('net.neoforged.neoforge.common.NeoForge');
      var KeyEvent = Java.loadClass('net.neoforged.neoforge.client.event.InputEvent$Key');
      Bus.EVENT_BUS.post(new KeyEvent(code, scan || 0, 1, mods || 0));
      Bus.EVENT_BUS.post(new KeyEvent(code, scan || 0, 0, mods || 0));
      return {handled: MC.screen !== null};
    }
    scan = scan === undefined ? 0 : scan; mods = mods === undefined ? 0 : mods;
    var cancelled = CH.onScreenKeyPressedPre(s, code, scan, mods);
    var handled = cancelled || s.keyPressed(code, scan, mods);
    if (!cancelled) CH.onScreenKeyPressedPost(s, code, scan, mods);
    if (MC.screen === s) s.keyReleased(code, scan, mods);
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
      try { item.label = String(w.getMessage().getString()); item.enabled=Boolean(w.active); } catch (e2) { }
      out.push(item);
    }
    return out;
  }

  // 选择器保留祖先作用域，活动模态遮住的下层节点不参与命中。
  function candidates(selector) {
    selector = selector || {};
    var roots = layoutCached();
    var all = flatCached();
    var modals = all.filter(function(n) { return n.kind === 'MODAL' && n.visible !== false; });
    if (modals.length) roots = [modals[modals.length - 1]];
    var out = [];
    var win = windowInfo();
    function walk(nodes, ancestors, clip) {
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i];
        if (n.visible === false) continue;
        var r = {x: Math.max(clip.x, n.x), y: Math.max(clip.y, n.y),
          right: Math.min(clip.right, n.x + n.width), bottom: Math.min(clip.bottom, n.y + n.height)};
        var label = selector.lang ? lang(selector.lang) : selector.text;
        var labels = selector.lang_any ? selector.lang_any.map(lang) : selector.text_any;
        var matches = (!selector.kind || n.kind === selector.kind) &&
          (label === undefined || n.text === label) && (!labels || labels.indexOf(n.text) >= 0) &&
          (selector.enabled === undefined || n.enabled === selector.enabled) &&
          (!selector.within || ancestors.some(function(a) { return matchScope(a, selector.within); })) &&
          (!selector.has_text || flat([n]).some(function(c) { return c.text === selector.has_text; })) &&
          r.right > r.x && r.bottom > r.y;
        if (matches) {
          var value = {kind:n.kind, text:n.text, x:r.x, y:r.y, width:r.right-r.x, height:r.bottom-r.y,
            bounds:{x:n.x,y:n.y,width:n.width,height:n.height}, enabled:n.enabled, visible:true, ancestors:ancestors.map(function(a){return {kind:a.kind,text:a.text};})};
          out.push(value);
        }
        walk(n.children || [], ancestors.concat([n]), n.kind === 'FRAME' || n.kind === 'MODAL' ? r : clip);
      }
    }
    walk(roots, [], {x:0,y:0,right:win.logicalWidth,bottom:win.logicalHeight});
    if (!all.length || all[0].kind === 'ERROR') {
      widgets().forEach(function(w) {
        var label = selector.lang ? lang(selector.lang) : selector.text;
        if ((!selector.kind || selector.kind === 'CONTROL') && (label === undefined || w.label === label)) {
          out.push({kind:'CONTROL',text:w.label,x:w.x,y:w.y,width:w.width,height:w.height,enabled:w.enabled !== false,visible:true,ancestors:[]});
        }
      });
    }
    return out;
  }
  function matchScope(node, scope) {
    return (!scope.kind || node.kind === scope.kind) &&
      (!scope.lang || node.text === lang(scope.lang)) && (scope.text === undefined || node.text === scope.text);
  }
  function unique(selector) {
    var nodes = candidates(selector);
    if (nodes.length !== 1) throw new Error('selector expected one: ' + JSON.stringify({selector:selector,candidates:nodes,screen:screenName()}));
    return nodes[0];
  }
  function clickTarget(selector) {
    var node = unique(selector);
    if (!node.enabled) throw new Error('disabled target: ' + JSON.stringify(node));
    var result = click(node.x + node.width/2, node.y + node.height/2, 0);
    if (!result.handled) throw new Error('input unhandled: ' + JSON.stringify({selector:selector,result:result}));
    return result;
  }
  function clickWidget(label) { return clickTarget({text:label, kind:'CONTROL'}); }
  function clickWidgetAny(labels) {
    for (var i=0;i<labels.length;i++) {
      if (candidates({text:labels[i],kind:'CONTROL'}).length) return clickWidget(labels[i]);
    }
    throw new Error('navigation widget missing: '+JSON.stringify(labels));
  }
  function read(query) {
    if (query.source === 'screen_title') return screenInfo().title;
    if (query.source === 'window') return windowInfo();
    if (query.source === 'layout') return layoutCached();
    if (query.source === 'workspace') return workspace();
    var nodes = candidates(query.selector || {});
    if (query.source === 'nodes') return nodes;
    if (query.source === 'count') return nodes.length;
    var node = unique(query.selector || {});
    if (query.source === 'text') return node.text;
    if (query.source === 'enabled') return node.enabled;
    if (query.source === 'rect') return {x:node.bounds.x,y:node.bounds.y,width:node.bounds.width,height:node.bounds.height,window:windowInfo()};
    throw new Error('unknown query: '+JSON.stringify(query));
  }
  function action(action) {
    if (action.kind === 'click') return clickTarget(action.selector);
    if (action.kind === 'type') return typeText(action.text);
    if (action.kind === 'clear') { key(269,0,0); for(var i=0;i<256;i++) key(259,0,0); return {handled:true}; }
    if (action.kind === 'key') return key(action.code,0,action.modifiers || 0);
    if (action.kind === 'resize') return resize(action.width,action.height,action.scale);
    if (action.kind === 'scroll') { var n=unique(action.selector);return wheel(n.x+n.width/2,n.y+n.height/2,action.delta); }
    if (action.kind === 'drag') { var n=unique(action.selector);return drag(n.x+n.width*action.from,n.y+n.height/2,n.x+n.width*action.to,n.y+n.height/2,4,0); }
    if (action.kind === 'press') {var n=unique(action.selector);var x=n.x+n.width*action.at;return {handled:Boolean(MC.screen.mouseClicked(x,n.y+n.height/2,0))};}
    if (action.kind === 'release') return {handled:Boolean(MC.screen.mouseReleased(0,0,0))};
    throw new Error('unknown action: '+JSON.stringify(action));
  }
  function reachStep(target) {
    var screen=screenName();
    if (target.kind === 'workbench' || target.kind === 'scene') {
      if (target.scene === 'previews' && !inWorld()) return menuStep(target.world);
      if (screen === 'PreviewExamplesScreen' && target.scene === 'previews') return {done:true};
      if (screen === 'Gallery' && target.kind === 'scene' && candidates({lang:'gui.itemdespawntowhat.debug.workbench.'+target.scene,kind:'FRAME'}).length === 1) return {done:true};
      if (screen === 'Directory') {
        if (target.kind === 'workbench') return {done:true};
        return {action:clickTarget({lang:'gui.itemdespawntowhat.debug.workbench.'+target.scene,kind:'CONTROL'})};
      }
      return {action:key(297,0,2)};
    }
    var menu=menuStep(target.world);
    if (!menu.done) return menu;
    if (screen !== 'RuleEditorScreen') {
      if (String(Workspace.instance().session().state()) === 'ACTIVE') {
        if (screen === 'Gallery' || screen === 'PreviewExamplesScreen') return {action:key(256)};
        if (screen === 'Directory') return {action:clickTarget({lang:'gui.itemdespawntowhat.debug.workbench.drafts',kind:'CONTROL'})};
      }
      return editorStep();
    }
    var back={lang:'gui.itemdespawntowhat.edit.button.back',kind:'CONTROL'};
    if (candidates(back).length === 1) return {action:clickTarget(back)};
    if (candidates({lang:'gui.itemdespawntowhat.edit.button.new',kind:'CONTROL'}).length === 1) return {done:true};
    return {action:key(256)};
  }

  function screenName() {
    var s = MC.screen;
    return s === null ? 'null' : String(s).split('@')[0].split('.').pop().split('$').pop();
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
          if (worldName == null || name === worldName) { chosenIndex = k; break; }
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
    var state = String(Workspace.instance().session().state());
    if (state === 'ACTIVE' && (screen === 'null' || screen === 'PauseScreen')) {
      key(297,0,2); return {done:false,action:'workbench-for-workspace'};
    }
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

  function layoutCached() {
    if (cachedLayout === null) cachedLayout = layout();
    return cachedLayout;
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

  function flatCached() {
    if (cachedFlat === null) cachedFlat = flat(layoutCached());
    return cachedFlat;
  }

  function hashText(text) {
    var h = 5381;
    for (var i = 0; i < text.length; i++) {
      h = ((h * 33) ^ text.charCodeAt(i)) | 0;
    }
    return (h >>> 0).toString(16);
  }

  // 轻量状态指纹：只覆盖可见节点的几何与文案，供驱动轮询判断界面是否已稳定。
  function probeKey() {
    var nodes = flatCached();
    var parts = [];
    var count = 0;
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (!n.visible || n.width <= 0 || n.height <= 0) continue;
      count++;
      parts.push(n.kind + ',' + n.x + ',' + n.y + ',' + n.width + ',' + n.height + ','
        + n.text + ',' + (n.enabled ? 1 : 0));
    }
    var w = windowInfo();
    return { s: screenName(), w: w.logicalWidth, h: w.logicalHeight, k: hashText(parts.join('|')), n: count };
  }

  function rects(kinds) {
    var wanted = kinds === undefined ? null : kinds;
    var nodes = flatCached();
    var out = [];
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (n.kind === 'ERROR') continue;
      if (n.width <= 0 || n.height <= 0) continue;
      if (wanted !== null && wanted.indexOf(n.kind) < 0) continue;
      out.push({ kind: n.kind, x: n.x, y: n.y, w: n.width, h: n.height, label: n.text });
    }
    return out;
  }

  function textNodes() {
    var nodes = flatCached();
    var out = [];
    for (var i = 0; i < nodes.length; i++) {
      if (nodes[i].text) out.push(nodes[i].text);
    }
    return out;
  }

  // 语言文件真值：按 key 取客户端当前语言下的展示文案。用例用它定位点击目标，
  // 这样中英文客户端都能点中同一个按钮，也不必把展示文案写进脚本。
  function lang(key) {
    try {
      return String(Language.getInstance().getOrDefault(String(key)));
    } catch (e) {
      return '';
    }
  }

  function screenInfo() {
    var s = MC.screen;
    if (s === null) return { screen: 'null', title: '' };
    var title = '';
    try { title = String(s.getTitle().getString()); } catch (e) { }
    return { screen: String(s).split('@')[0].split('.').pop().split('$').pop(), className: String(s.getClass ? s.getClass().getName() : s), title: title };
  }

  function state() {
    var s = MC.screen;
    var out = {
      where: String(AutoJS.where()),
      screen: s === null ? 'null' : String(s).split('@')[0].split('.').pop().split('$').pop(),
      player: MC.player === null ? 'null' : String(MC.player.getName().getString()),
      connected: MC.getConnection() !== null,
      workspace: String(Workspace.instance().session().state())
    };
    var server = MC.getSingleplayerServer();
    out.singleplayerServer = server === null ? 'null' : String(server.isRunning());
    return out;
  }

  // 从工作区发布的会话与保存快照读取状态，接口失败由探针正常报告。
  function workspace() {
    var ws = Workspace.instance();
    var session = ws.session();
    var snap = session.snapshot();
    return {
      state: String(session.state()),
      sessionId: String(session.sessionId()),
      resultSeq: ws.saving().outcome().sequence(),
      entries: snap === null ? -1 : snap.entries().size()
    };
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

  function fps() {
    try {
      var value = MC.getFps();
      return typeof value === 'number' ? Math.round(value) : 0;
    } catch (e) {
      return 0;
    }
  }

  // 轻量运行时信息：eval 内耗时从 begin() 起算，fps 与帧缓冲尺寸供驱动算截图前的帧间隔。
  function runtime() {
    var w = windowInfo();
    return {
      fps: fps(),
      eval_ms: beganAt ? (Date.now() - beganAt) : 0,
      fbWidth: w.fbWidth, fbHeight: w.fbHeight,
      logicalWidth: w.logicalWidth, logicalHeight: w.logicalHeight, scale: w.scale
    };
  }

  return {
    __version: VERSION,
    mc: MC,
    begin: begin,
    window: windowInfo,
    resize: resize,
    shot: shot,
    click: click,
    drag: drag,
    wheel: wheel,
    key: key,
    typeText: typeText,
    widgets: widgets,
    candidates:candidates, unique:unique, read:read, action:action, reachStep:reachStep,
    clickWidget: clickWidget,
    clickWidgetAny: clickWidgetAny,
    screenName: screenName,
    menuStep: menuStep,
    editorStep: editorStep,
    layout: layout,
    layoutCached: layoutCached,
    flat: flat,
    flatCached: flatCached,
    probeKey: probeKey,
    rects: rects,
    textNodes: textNodes,
    lang: lang,
    screenInfo: screenInfo,
    state: state,
    workspace: workspace,
    openEditor: openEditor,
    dumpJson: dumpJson,
    fps: fps,
    runtime: runtime
  };
})();
