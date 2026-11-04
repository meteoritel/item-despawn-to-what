// GUI 探针的客户端原语集合：由驱动在会话内安装一次，之后每次 eval 只发表达式本身。
// 只做「取得真实状态 / 走真实输入路径 / 截图 / 导出布局快照」，不含任何断言与期望值。
// describe 提供协议与内容身份；invoke 是驱动唯一的业务入口。
var __gp = (function () {
  var PROTOCOL_MAJOR = 1;
  var SOURCE_HASH = String(__gpSourceHash);
  var RUN_DIR = String(__gpRunDir);
  var MC = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  var SS = Java.loadClass('net.minecraft.client.Screenshot');
  var File = Java.loadClass('java.io.File');
  var CH = Java.loadClass('net.neoforged.neoforge.client.ClientHooks');
  var GLFW = Java.loadClass('org.lwjgl.glfw.GLFW');
  var Integer = Java.loadClass('java.lang.Integer');
  var NativeImage = Java.loadClass('com.mojang.blaze3d.platform.NativeImage');
  var Workspace = Java.loadClass('com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace');
  var Language = Java.loadClass('net.minecraft.locale.Language');
  var EventBus = Java.loadClass('net.neoforged.neoforge.common.NeoForge').EVENT_BUS;
  var RenderPost = Java.loadClass('net.neoforged.neoforge.client.event.RenderFrameEvent$Post');
  var EventPriority = Java.loadClass('net.neoforged.bus.api.EventPriority');
  var Consumer = Java.loadClass('java.util.function.Consumer');

  // 证据目录与本次安装绑定，换目录时由 adapter 重装。
  function join(name) {
    return RUN_DIR + '/' + name;
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

  // 只在 RenderFrameEvent.Post 调用，读取当前已完成渲染的帧缓冲。
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

  // 截图任务只在等待期间订阅帧事件；完成、超时、取消或重装时释放。
  var pendingShot = null;

  function detachShot(task) {
    if (task.listener !== null) {
      EventBus.unregister(task.listener);
      task.listener = null;
    }
  }

  function cancelShot() {
    if (pendingShot !== null) detachShot(pendingShot);
    pendingShot = null;
    return {cancelled:true};
  }

  function requestShot(payload) {
    if (pendingShot !== null) throw new Error('Screenshot already pending');
    var started = Date.now();
    var task = {name:String(payload.name), started:started, deadline:started + payload.timeout_ms,
      signature:null, stableFrames:0, listener:null, result:null, error:null};
    task.listener = new Consumer({accept:function() {
      try {
        if (Date.now() >= task.deadline) {
          task.error = 'Screenshot render wait timed out';
          return;
        }
        begin();
        if (MC.getOverlay() !== null) {
          task.signature = null;
          task.stableFrames = 0;
          return;
        }
        var signature = JSON.stringify(sample().signature);
        task.stableFrames = signature === task.signature ? task.stableFrames + 1 : 1;
        task.signature = signature;
        if (task.stableFrames < 2) return;
        task.result = shot(task.name);
        task.result.shot_ms = Date.now() - task.started;
      } catch (error) {
        task.error = String(error);
      } finally {
        if (task.result !== null || task.error !== null) detachShot(task);
      }
    }});
    EventBus.addListener(EventPriority.LOWEST, RenderPost, task.listener);
    pendingShot = task;
    return {pending:true};
  }

  function shotStatus() {
    if (pendingShot === null) throw new Error('No screenshot pending');
    if (pendingShot.error !== null) throw new Error(pendingShot.error);
    if (pendingShot.result === null) return {pending:true};
    var result = pendingShot.result;
    pendingShot = null;
    return result;
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
          (!selector.has_text || flat([n]).some(function(c) { return c.text.indexOf(selector.has_text) >= 0; })) &&
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
    if (nodes.length !== 1) throw new Error('selector expected one: ' + JSON.stringify({selector:selector,candidates:nodes,screen:screenName(),layoutCount:layoutCached().length}));
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
  var QUERY_HANDLERS = {
    screen_title:function(q) { return screenInfo().title; },
    window:function(q) { return windowInfo(); },
    layout:function(q) { return layoutCached(); },
    workspace:function(q) { return workspace(); },
    nodes:function(q) { return candidates(q.selector || {}); },
    count:function(q) { return candidates(q.selector || {}).length; },
    text:function(q) { return unique(q.selector || {}).text; },
    enabled:function(q) { return unique(q.selector || {}).enabled; },
    rect:function(q) { var n=unique(q.selector || {}); return {x:n.bounds.x,y:n.bounds.y,width:n.bounds.width,height:n.bounds.height,window:windowInfo()}; }
  };
  function read(query) {
    if (!Object.prototype.hasOwnProperty.call(QUERY_HANDLERS, query.source)) throw new Error('unknown query: '+JSON.stringify(query));
    return QUERY_HANDLERS[query.source](query);
  }
  var ACTION_HANDLERS = {
    click:function(a) { return clickTarget(a.selector); },
    type:function(a) { return typeText(a.text); },
    clear:function(a) { key(269,0,0); for(var i=0;i<256;i++) key(259,0,0); return {handled:true}; },
    key:function(a) { return key(a.code,0,a.modifiers || 0); },
    resize:function(a) { return resize(a.width,a.height,a.scale); },
    scroll:function(a) { var n=unique(a.selector);return wheel(n.x+n.width/2,n.y+n.height/2,a.delta); },
    drag:function(a) { var n=unique(a.selector);var v=a.axis==='y';return drag(n.x+n.width*(v?0.5:a.from),n.y+n.height*(v?a.from:0.5),n.x+n.width*(v?0.5:a.to),n.y+n.height*(v?a.to:0.5),4,0); },
    press:function(a) { var n=unique(a.selector);var v=a.axis==='y';return {handled:Boolean(MC.screen.mouseClicked(n.x+n.width*(v?0.5:a.at),n.y+n.height*(v?a.at:0.5),0))}; },
    release:function(a) { return {handled:Boolean(MC.screen.mouseReleased(0,0,0))}; }
  };
  function action(value) {
    if (!Object.prototype.hasOwnProperty.call(ACTION_HANDLERS, value.kind)) throw new Error('unknown action: '+JSON.stringify(value));
    return ACTION_HANDLERS[value.kind](value);
  }

  var START_HANDLERS = {
    editor:reachEditor,
    workbench:reachWorkbench,
    scene:reachScene
  };
  var SCENE_HANDLERS = {
    kit_showcase:reachWorkbench,
    gallery:reachWorkbench,
    interactions:reachWorkbench,
    previews:reachWorkbench
  };
  function reachStep(target) {
    if (MC.getOverlay() !== null) return {done:false,action:"wait-resource-overlay"};
    if (!Object.prototype.hasOwnProperty.call(START_HANDLERS, target.kind)) throw new Error('unknown start: '+JSON.stringify(target));
    return START_HANDLERS[target.kind](target);
  }
  function reachScene(target) {
    if (!Object.prototype.hasOwnProperty.call(SCENE_HANDLERS, target.scene)) throw new Error('unknown scene: '+JSON.stringify(target));
    return SCENE_HANDLERS[target.scene](target);
  }
  function reachWorkbench(target) {
    var screen = screenName();
      if (target.scene === 'previews' && !inWorld()) return menuStep(target.world);
      if (screen === 'PreviewExamplesScreen' && target.scene === 'previews') return {done:true};
      if (screen === 'KitShowcaseScreen' && target.scene === 'kit_showcase') return {done:true};
      if (screen === 'Gallery' && target.kind === 'scene' && candidates({lang:'gui.itemdespawntowhat.debug.workbench.'+target.scene,kind:'FRAME'}).length === 1) return {done:true};
      if (screen === 'Directory') {
        if (layoutCached().length === 0) return {done:false,action:'wait-directory-layout'};
        if (target.kind === 'workbench') return {done:true};
        return {action:clickTarget({lang:'gui.itemdespawntowhat.debug.workbench.'+target.scene,kind:'CONTROL'})};
      }
      return {action:key(297,0,2)};
  }
  function reachEditor(target) {
    var screen = screenName();
    var menu=menuStep(target.world);
    if (!menu.done) return menu;
    if (screen !== 'RuleEditorScreen') {
      if (String(Workspace.instance().session().state()) === 'ACTIVE') {
        if (screen === 'Gallery' || screen === 'PreviewExamplesScreen' || screen === 'KitShowcaseScreen') return {action:key(256)};
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
    if (screen === 'KitShowcaseScreen' || screen === 'Gallery' || screen === 'Directory') {
      return { done: false, action: 'leave-local-workbench', result: key(256) };
    }
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

  // 轻量运行时信息：eval 内耗时从 begin() 起算，fps 用于调节状态轮询间隔。
  function runtime() {
    var w = windowInfo();
    return {
      fps: fps(),
      eval_ms: beganAt ? (Date.now() - beganAt) : 0,
      fbWidth: w.fbWidth, fbHeight: w.fbHeight,
      logicalWidth: w.logicalWidth, logicalHeight: w.logicalHeight, scale: w.scale
    };
  }

  function environment() {
    return {window:windowInfo(), state:state(), runtime:runtime()};
  }

  function sample() {
    var k = probeKey();
    var t = runtime();
    k.fps = t.fps; k.scale = t.scale; k.fbw = t.fbWidth; k.fbh = t.fbHeight;
    // 比较口径只在此声明；FPS 与节点数保留为诊断。
    k.signature = [k.s, k.w, k.h, k.k, k.scale, k.fbw, k.fbh];
    return k;
  }

  var verbs = {
    environment:environment,
    sample:sample,
    read:read,
    action:function(payload) { var result = action(payload); return {r:result, k:sample()}; },
    reach:reachStep,
    language:lang,
    screenshot:requestShot,
    screenshot_status:shotStatus,
    cancel_screenshot:cancelShot
  };

  function describe() {
    begin();
    return {protocol_major:PROTOCOL_MAJOR, capabilities:Object.keys(verbs).sort(),
      source_hash:SOURCE_HASH, run_dir:RUN_DIR, env:environment(),
      contract:{actions:Object.keys(ACTION_HANDLERS).sort(), queries:Object.keys(QUERY_HANDLERS).sort(),
        starts:Object.keys(START_HANDLERS).sort(), scenes:Object.keys(SCENE_HANDLERS).sort()}};
  }

  function invoke(request) {
    begin();
    try {
      if (!request || request.protocol_major !== PROTOCOL_MAJOR) throw new Error('GUI protocol major mismatch');
      if (!Object.prototype.hasOwnProperty.call(verbs, request.verb)) throw new Error('Unknown GUI verb: ' + request.verb);
      return {protocol_major:PROTOCOL_MAJOR, ok:true, value:verbs[request.verb](request.payload)};
    } catch (error) {
      return {protocol_major:PROTOCOL_MAJOR, ok:false, error:String(error)};
    }
  }

  return {describe:describe, invoke:invoke, dispose:cancelShot};
})();
