(() => {
  if (window.__prosperityCaptureInstalled) return;
  window.__prosperityCaptureInstalled = true;

  let socket = null;
  let running = false;
  let timer = null;
  let panel = null;
  let statusNode = null;
  let startButton = null;

  function setStatus(text) {
    if (statusNode) statusNode.textContent = text;
  }

  function visiblePageText() {
    // innerText reflects rendered/visible text; do not inspect hidden DOM, inputs,
    // cookies, storage, performance entries, or network requests.
    const text = document.body ? document.body.innerText : "";
    return (text || "").slice(0, 120000);
  }

  function sendSnapshot() {
    if (!running || !socket || socket.readyState !== WebSocket.OPEN || document.visibilityState !== "visible") return;
    const text = visiblePageText();
    socket.send(JSON.stringify({
      type: "visible_snapshot",
      data: {
        source_url: location.origin + location.pathname,
        page_title: document.title,
        text
      }
    }));
    setStatus("Live · " + new Date().toLocaleTimeString());
  }

  function stop() {
    running = false;
    if (timer) clearInterval(timer);
    timer = null;
    if (socket) socket.close();
    socket = null;
    if (startButton) startButton.textContent = "Start visible capture";
    setStatus("Stopped");
  }

  function start() {
    if (running) return;
    if (typeof PROSPERITY_BRIDGE_TOKEN === "undefined" ||
        PROSPERITY_BRIDGE_TOKEN === "PASTE_TOKEN_FROM_BRIDGE_TERMINAL") {
      setStatus("Set pairing token in config.js, then reload extension");
      return;
    }
    setStatus("Connecting to local bridge…");
    socket = new WebSocket(PROSPERITY_BRIDGE_WS);
    socket.onopen = () => {
      socket.send(JSON.stringify({type: "auth", role: "capture", token: PROSPERITY_BRIDGE_TOKEN}));
    };
    socket.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === "status" && msg.connected) {
          running = true;
          startButton.textContent = "Stop capture";
          sendSnapshot();
          timer = setInterval(sendSnapshot, 1500);
          setStatus("Connected · capturing visible text");
        }
      } catch (_) {}
    };
    socket.onerror = () => setStatus("Cannot reach bridge · is it running on this PC?");
    socket.onclose = () => {
      if (running) setStatus("Bridge disconnected");
      running = false;
      if (timer) clearInterval(timer);
      timer = null;
      if (startButton) startButton.textContent = "Start visible capture";
    };
  }

  function buildPanel() {
    panel = document.createElement("div");
    panel.style.cssText = "position:fixed;z-index:2147483647;right:14px;bottom:14px;background:#111827;color:white;padding:12px;border-radius:10px;font:13px Arial;box-shadow:0 4px 18px #0006;width:230px";
    const title = document.createElement("div");
    title.textContent = "Prosperity · visible capture";
    title.style.cssText = "font-weight:bold;margin-bottom:8px";
    statusNode = document.createElement("div");
    statusNode.textContent = "Ready · user-controlled";
    statusNode.style.cssText = "font-size:11px;color:#cbd5e1;margin-bottom:8px";
    startButton = document.createElement("button");
    startButton.textContent = "Start visible capture";
    startButton.style.cssText = "padding:7px 9px;border:0;border-radius:6px;cursor:pointer";
    startButton.addEventListener("click", () => running ? stop() : start());
    const note = document.createElement("div");
    note.textContent = "Sends rendered page text only. Stop before viewing personal account pages.";
    note.style.cssText = "font-size:10px;color:#cbd5e1;margin-top:8px;line-height:1.35";
    panel.append(title, statusNode, startButton, note);
    document.documentElement.appendChild(panel);
  }

  const wait = setInterval(() => {
    if (document.body && !panel) {
      clearInterval(wait);
      buildPanel();
    }
  }, 500);
})();
