class BrowserStreamElement extends HTMLElement {
  #socket = null
  #canvas = null
  #resizeObserver = null
  #sessionId = null
  #streamPath = '/browser-stream'
  #activeTabId = null
  #tabs = []
  #stream = {quality: 75, intervalMillis: 40, networkEnabled: false, bandwidthBytesPerSecond: 500 * 1024, latencyMillis: 0}
  #telemetry = null
  #stats = {frames: 0, bytes: 0, renderFrames: 0, windowStart: 0, fps: 0, renderFps: 0, frameBytes: 0, lastFrameBytes: 0, droppedFrames: 0, serverLatencyMs: -1}
  #pending = null
  #chunks = new Map()
  #drawing = false
  #latestFrameId = -1
  #moveFrame = 0
  #pendingMove = null
  #pointerSequence = 0
  #qualityTimer = null
  #focusAddressOnTabChange = false
  #socketGeneration = 0

  connectedCallback() {
    this.#build()
    this.#observeViewport()
    this.#connect()
  }

  disconnectedCallback() {
    this.#socketGeneration += 1
    this.#resizeObserver?.disconnect()
    this.#resizeObserver = null
    clearTimeout(this.#qualityTimer)
    if (this.#moveFrame) cancelAnimationFrame(this.#moveFrame)
    this.#moveFrame = 0
    this.#pendingMove = null
    this.#socket?.close()
    this.#socket = null
  }

  get streamPath() {
    return this.#streamPath
  }

  set streamPath(value) {
    this.#streamPath = value || '/browser-stream'
    this.#connect()
  }

  get sessionId() {
    return this.#sessionId
  }

  set sessionId(value) {
    this.#sessionId = value
    this.#connect()
  }

  #build() {
    if (this.#canvas) return
    const root = document.createElement('div')
    root.className = 'browser-stream-root'
    root.style.cssText = 'display:flex;flex-direction:column;gap:6px;font-size:13px'

    const strip = document.createElement('div')
    strip.style.cssText = 'display:flex;align-items:center;gap:4px;flex-wrap:wrap'
    this.tabStrip = document.createElement('div')
    this.tabStrip.style.cssText = 'display:flex;align-items:center;gap:2px;flex:1;min-width:0;overflow-x:auto'
    this.newTabButton = document.createElement('button')
    this.newTabButton.type = 'button'
    this.newTabButton.textContent = '新建标签页'
    this.newTabButton.title = '新建标签页并聚焦地址栏'
    this.newTabButton.disabled = true
    this.newTabButton.style.cssText = 'padding:3px 10px;cursor:pointer'
    this.newTabButton.addEventListener('click', () => {
      this.#focusAddressOnTabChange = true
      this.#send({type: 'tab.open', url: 'about:blank'})
    })
    strip.append(this.tabStrip, this.newTabButton)

    const addressRow = document.createElement('div')
    addressRow.style.cssText = 'display:flex;align-items:center;gap:6px'
    this.address = document.createElement('input')
    this.address.type = 'text'
    this.address.placeholder = '输入网址，例如 example.org'
    this.address.disabled = true
    this.address.autocomplete = 'off'
    this.address.spellcheck = false
    this.address.style.cssText = 'flex:1;min-width:0;padding:4px 8px;font-size:13px'
    this.address.addEventListener('keydown', event => {
      if (event.key === 'Enter') {
        event.preventDefault()
        this.#navigate()
      }
    })
    const openButton = document.createElement('button')
    openButton.type = 'button'
    openButton.textContent = '打开'
    openButton.disabled = true
    openButton.style.cssText = 'padding:3px 12px;cursor:pointer'
    openButton.addEventListener('click', () => this.#navigate())
    this.panelToggle = document.createElement('button')
    this.panelToggle.type = 'button'
    this.panelToggle.textContent = '画面参数'
    this.panelToggle.style.cssText = 'padding:3px 12px;cursor:pointer'
    this.panelToggle.addEventListener('click', () => {
      this.panel.hidden = !this.panel.hidden
      this.panelToggle.textContent = this.panel.hidden ? '画面参数' : '收起参数'
    })
    this.openButton = openButton
    addressRow.append(this.address, openButton, this.panelToggle)

    this.navigationStatus = document.createElement('span')
    this.navigationStatus.style.cssText = 'display:block;min-height:18px;color:#666'

    this.panel = document.createElement('div')
    this.panel.hidden = true
    this.panel.style.cssText = 'display:flex;flex-wrap:wrap;gap:12px;align-items:center;padding:6px 8px;border:1px solid rgba(128,128,128,.35);border-radius:4px'
    this.qualityInput = this.#slider(20, 90, 5, this.#stream.quality, '质量', value => {
      this.#stream.quality = value
      this.#scheduleQuality()
    })
    this.intervalInput = this.#slider(40, 600, 20, this.#stream.intervalMillis, '间隔', value => {
      this.#stream.intervalMillis = value
      this.#scheduleQuality()
    })
    const networkRow = document.createElement('label')
    networkRow.style.cssText = 'display:flex;align-items:center;gap:6px'
    this.networkEnabledInput = document.createElement('input')
    this.networkEnabledInput.type = 'checkbox'
    this.networkEnabledInput.checked = this.#stream.networkEnabled
    this.networkEnabledInput.addEventListener('change', () => {
      this.#stream.networkEnabled = this.networkEnabledInput.checked
      this.#scheduleQuality()
    })
    networkRow.append(this.networkEnabledInput, document.createTextNode('网络模拟'))
    this.bandwidthInput = document.createElement('select')
    for (const value of [64, 128, 256, 500, 1024, 2048]) {
      const option = document.createElement('option')
      option.value = String(value * 1024)
      option.textContent = `${value} KB/s`
      this.bandwidthInput.append(option)
    }
    this.bandwidthInput.value = String(this.#stream.bandwidthBytesPerSecond)
    this.bandwidthInput.addEventListener('change', () => {
      this.#stream.bandwidthBytesPerSecond = Number(this.bandwidthInput.value)
      this.#scheduleQuality()
    })
    this.latencyInput = this.#slider(0, 300, 10, this.#stream.latencyMillis, '延迟', value => {
      this.#stream.latencyMillis = value
      this.#scheduleQuality()
    })
    this.metrics = document.createElement('span')
    this.metrics.style.cssText = 'opacity:.75'
    this.panel.append(networkRow, this.bandwidthInput, this.qualityInput.row, this.intervalInput.row, this.latencyInput.row, this.metrics)

    const canvasFrame = document.createElement('div')
    canvasFrame.style.cssText = 'position:relative;display:block;width:100%;height:calc(100vh - 210px);min-height:480px;background:#111;overflow:hidden'
    const canvas = document.createElement('canvas')
    canvas.width = 1280
    canvas.height = 720
    canvas.tabIndex = 0
    canvas.style.cssText = 'display:block;width:100%;height:100%;background:#111;outline:none;cursor:default'
    canvas.addEventListener('pointerdown', event => this.#pointerDown(event))
    canvas.addEventListener('pointermove', event => this.#pointerMove(event))
    canvas.addEventListener('pointerup', event => this.#pointerUp(event))
    canvas.addEventListener('pointercancel', event => this.#pointerCancel(event))
    canvas.addEventListener('wheel', event => this.#wheel(event), {passive: false})
    canvas.addEventListener('keydown', event => this.#key(event))
    canvas.addEventListener('compositionend', event => {
      if (event.data) this.#send({type: 'text', text: event.data})
    })
    canvas.addEventListener('contextmenu', event => event.preventDefault())
    document.addEventListener('paste', event => {
      if (document.activeElement !== this.#canvas) return
      const text = event.clipboardData?.getData('text/plain')
      if (!text) return
      event.preventDefault()
      this.#send({type: 'text', text})
    })
    this.hud = document.createElement('div')
    this.hud.style.cssText = 'position:absolute;left:8px;top:8px;right:8px;min-height:20px;padding:3px 6px;background:rgba(0,0,0,.62);color:#fff;font:12px/1.4 monospace;pointer-events:none;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'
    canvasFrame.append(canvas, this.hud)
    this.#canvas = canvas

    this.infoPanel = document.createElement('div')
    this.infoPanel.style.cssText = 'display:grid;gap:3px;padding:6px 8px;font:12px/1.45 monospace;color:#555;background:rgba(128,128,128,.08);overflow-wrap:anywhere'
    this.serverInfo = document.createElement('div')
    this.browserInfo = document.createElement('div')
    this.infoPanel.append(this.serverInfo, this.browserInfo)

    root.append(strip, addressRow, this.navigationStatus, this.panel, canvasFrame, this.infoPanel)
    this.append(root)
    this.#renderTabs()
    this.#renderMetrics()
    this.#renderHud()
    this.#renderInfo()
  }

  #observeViewport() {
    if (!this.#canvas || this.#resizeObserver) return
    this.#resizeObserver = new ResizeObserver(() => this.#syncViewport())
    this.#resizeObserver.observe(this.#canvas)
  }

  #syncViewport() {
    if (!this.#canvas) return
    const box = this.#canvas.getBoundingClientRect()
    const width = Math.max(320, Math.round(box.width))
    const height = Math.max(240, Math.round(box.height))
    if (this.#canvas.width !== width || this.#canvas.height !== height) {
      this.#canvas.width = width
      this.#canvas.height = height
      this.#renderMetrics()
      this.#renderHud()
    }
    this.#send({type: 'viewport.resize', width, height})
  }

  #slider(min, max, step, value, label, onChange) {
    const row = document.createElement('label')
    row.style.cssText = 'display:flex;align-items:center;gap:6px'
    const text = document.createElement('span')
    text.textContent = label
    const input = document.createElement('input')
    input.type = 'range'
    input.min = String(min)
    input.max = String(max)
    input.step = String(step)
    input.value = String(value)
    const readout = document.createElement('span')
    readout.textContent = String(value)
    input.addEventListener('input', () => {
      const next = Number(input.value)
      readout.textContent = String(next)
      onChange(next)
    })
    row.append(text, input, readout)
    return {row, input, readout}
  }

  #scheduleQuality() {
    clearTimeout(this.#qualityTimer)
    this.#qualityTimer = setTimeout(() => {
      this.#send({
        type: 'stream.configure',
        quality: this.#stream.quality,
        intervalMillis: this.#stream.intervalMillis,
        networkEnabled: this.#stream.networkEnabled,
        bandwidthBytesPerSecond: this.#stream.bandwidthBytesPerSecond,
        latencyMillis: this.#stream.latencyMillis,
      })
    }, 120)
    this.#renderMetrics()
  }

  #connect() {
    const id = this.#sessionId
    if (!id || !this.#canvas) return
    const generation = ++this.#socketGeneration
    this.#socket?.close()
    this.#latestFrameId = -1
    this.#chunks.clear()
    this.address.disabled = true
    this.openButton.disabled = true
    this.newTabButton.disabled = true
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
    const socket = new WebSocket(`${scheme}://${location.host}${this.#streamPath}/${id}`)
    socket.binaryType = 'arraybuffer'
    socket.onopen = () => {
      if (generation !== this.#socketGeneration) return
      this.address.disabled = false
      this.openButton.disabled = false
      this.newTabButton.disabled = false
      this.#syncViewport()
      this.#renderHud()
      this.#renderInfo()
    }
    socket.onclose = () => {
      if (generation !== this.#socketGeneration) return
      this.address.disabled = true
      this.openButton.disabled = true
      this.newTabButton.disabled = true
      this.#renderHud()
      this.#renderInfo()
    }
    socket.onmessage = event => {
      if (generation !== this.#socketGeneration) return
      if (typeof event.data === 'string') {
        const message = JSON.parse(event.data)
        if (message.type === 'telemetry') this.#applyTelemetry(message)
        else if (message.type === 'cursor') this.#applyCursor(message.value)
        else if (message.type === 'clipboard') this.#copyToLocal(message.text)
        else this.#applyState(message)
      } else this.#renderChunk(event.data)
    }
    this.#socket = socket
    this.#stats.windowStart = performance.now()
  }

  #applyState(state) {
    const previousActive = this.#activeTabId
    this.#tabs = state.tabs || []
    this.#activeTabId = state.activeTabId
    this.#stream = {
      quality: state.quality,
      intervalMillis: state.intervalMillis,
      networkEnabled: state.networkEnabled,
      bandwidthBytesPerSecond: state.bandwidthBytesPerSecond,
      latencyMillis: state.latencyMillis,
    }
    this.qualityInput.input.value = String(state.quality)
    this.qualityInput.readout.textContent = String(state.quality)
    this.intervalInput.input.value = String(state.intervalMillis)
    this.intervalInput.readout.textContent = String(state.intervalMillis)
    this.networkEnabledInput.checked = state.networkEnabled
    this.bandwidthInput.value = String(state.bandwidthBytesPerSecond)
    this.latencyInput.input.value = String(state.latencyMillis)
    this.latencyInput.readout.textContent = String(state.latencyMillis)
    const active = this.#tabs.find(tab => tab.id === this.#activeTabId)
    const activeChanged = this.#activeTabId !== previousActive
    if (active && (activeChanged || document.activeElement !== this.address)) this.address.value = active.url
    this.#renderTabs()
    this.#renderNavigationStatus(active)
    this.#renderMetrics()
    this.#renderHud()
    this.#renderInfo()
    if (activeChanged && this.#focusAddressOnTabChange) {
      this.#focusAddressOnTabChange = false
      requestAnimationFrame(() => {
        this.address.focus({preventScroll: true})
        this.address.select()
      })
    }
  }

  #applyTelemetry(message) {
    this.#telemetry = message
    this.#stats.serverLatencyMs = Math.max(0, Date.now() - message.timestamp)
    this.#renderHud()
    this.#renderInfo()
  }

  #renderHud() {
    const telemetry = this.#telemetry
    const connection = telemetry?.connection
    const stream = telemetry?.stream
    const socketState = this.#socket ? ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'][this.#socket.readyState] : 'CLOSED'
    const serverFps = stream ? stream.sendFps.toFixed(1) : '0.0'
    const actualRate = stream ? `${this.#bytes(stream.bytesPerSecond)}/s` : '--'
    const cap = stream?.networkEnabled ? `${this.#bytes(stream.bandwidthBytesPerSecond)}/s` : '不限速'
    const latency = this.#stats.serverLatencyMs >= 0 ? `${this.#stats.serverLatencyMs} ms` : '--'
    const average = this.#stats.frameBytes ? `${(this.#stats.frameBytes / 1024).toFixed(1)} KB` : '--'
    this.hud.textContent = `${this.#canvas.width}×${this.#canvas.height} · 当前 ${this.#stats.lastFrameBytes} B · 均 ${average} · 收 ${this.#stats.fps.toFixed(1)} fps · 绘 ${this.#stats.renderFps.toFixed(1)} fps · 丢 ${this.#stats.droppedFrames} · 服务端 ${serverFps} fps · 实际 ${actualRate} / 限 ${cap} · 遥测 ${latency} · JPEG ${this.#stream.quality} · 间隔 ${this.#stream.intervalMillis} ms · WS ${connection?.open ? 'OPEN' : socketState}`
  }

  #renderInfo() {
    const telemetry = this.#telemetry
    if (!telemetry) {
      this.serverInfo.textContent = '服务器：等待遥测数据'
      this.browserInfo.textContent = '浏览器：等待遥测数据'
      return
    }
    const server = telemetry.server
    const connection = telemetry.connection
    const browser = telemetry.browser
    const input = browser.input || {}
    this.serverInfo.textContent = `服务器 | ${server.hostName} | ${server.osName} ${server.osVersion} ${server.osArch} | Java ${server.javaVersion} | JVM ${server.jvmName} | PID ${server.pid} | CPU ${this.#percent(server.processCpuPercent)} 进程 / ${this.#percent(server.systemCpuPercent)} 系统 | Heap ${this.#bytes(server.heapUsedBytes)} / ${this.#bytes(server.heapMaxBytes)} | RAM ${this.#bytes(server.totalMemoryBytes - server.freeMemoryBytes)} / ${this.#bytes(server.totalMemoryBytes)} | Uptime ${this.#duration(server.uptimeMillis)} | 本地 ${connection.localIp}:${connection.localPort} | 客户端 ${connection.remoteIp}:${connection.remotePort}`
    const stream = telemetry.stream
    const network = stream.networkEnabled
      ? `限速 ${this.#bytes(stream.bandwidthBytesPerSecond)} / 延迟 ${stream.latencyMillis} ms / 实际 ${this.#bytes(stream.bytesPerSecond)}/s`
      : '不限速'
    let runtime = '--'
    if (browser.headless !== null && browser.headless !== undefined) {
      runtime = browser.headless ? 'headless' : 'headed'
    }
    const failure = browser.lastFailure
      ? ` | 最近错误 ${browser.lastFailure.operation}: ${browser.lastFailure.message}`
      : ''
    this.browserInfo.textContent = `浏览器 | ${browser.engine} ${browser.browserVersion} | CDP ${browser.protocolVersion} | 运行 ${runtime} | 环境 ${browser.environmentMode || 'NATIVE'} | 配置 ${browser.profileId || '--'} | Context ${browser.contextOpen ? 'OPEN' : 'CLOSED'} | 标签页 ${browser.tabCount} | 当前 ${browser.activeTitle || '无标题'} | ${browser.activeUrl || 'about:blank'} | ${network} | 服务端帧 ${stream.framesSent} | 流量 ${this.#bytes(stream.bytesSent)} | 当前帧 ${this.#bytes(stream.lastFrameBytes)} | 输入 ${input.lastAppliedSequence ?? '--'}/${input.lastReceivedSequence ?? '--'} | 队列 ${input.pendingOperations ?? '--'} | 拒绝 ${input.rejectedOperations ?? '--'}${failure}`
  }

  #bytes(value) {
    if (value == null || !Number.isFinite(value) || value < 0) return '--'
    if (value < 1024) return `${value} B`
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
    return `${(value / 1024 / 1024).toFixed(1)} MB`
  }

  #percent(value) {
    return value == null || !Number.isFinite(value) || value < 0 ? '--' : `${value.toFixed(1)}%`
  }

  #duration(value) {
    if (value == null || value < 0) return '--'
    const seconds = Math.floor(value / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    return `${hours}h ${minutes % 60}m ${seconds % 60}s`
  }

  #renderTabs() {
    this.tabStrip.replaceChildren()
    for (const tab of this.#tabs) {
      const button = document.createElement('button')
      button.type = 'button'
      button.className = `tab ${tab.id === this.#activeTabId ? 'active' : ''}`
      button.title = tab.error || tab.url
      const label = document.createElement('span')
      label.className = 'tab-title'
      label.textContent = tab.loading ? '加载中…' : (tab.title || tab.url || '新标签页')
      const close = document.createElement('span')
      close.className = 'tab-close'
      close.textContent = '×'
      close.style.cssText = 'margin-left:8px;opacity:.6'
      close.addEventListener('click', event => {
        event.stopPropagation()
        this.#send({type: 'tab.close', tabId: tab.id})
      })
      button.append(label, close)
      button.style.cssText = `display:flex;align-items:center;max-width:220px;padding:3px 10px;cursor:pointer;font-size:12px;border:1px solid rgba(128,128,128,.35);border-radius:4px 4px 0 0;background:${tab.id === this.#activeTabId ? '#fff' : 'transparent'};font-weight:${tab.id === this.#activeTabId ? '600' : '400'}`
      label.style.cssText = 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap'
      button.addEventListener('click', () => this.#send({type: 'tab.activate', tabId: tab.id}))
      this.tabStrip.append(button)
    }
  }

  #renderNavigationStatus(active) {
    if (!active) {
      this.navigationStatus.textContent = '等待创建标签页'
      return
    }
    if (active.error) {
      this.navigationStatus.textContent = `导航失败：${active.error}`
      this.navigationStatus.style.color = '#b42318'
      return
    }
    this.navigationStatus.style.color = '#666'
    this.navigationStatus.textContent = active.loading ? `加载中：${active.url}` : ''
  }

  #renderMetrics() {
    const width = this.#canvas.width
    const height = this.#canvas.height
    const fps = this.#stream.intervalMillis > 0 ? (1000 / this.#stream.intervalMillis).toFixed(1) : '0'
    const measured = this.#stats.fps ? this.#stats.fps.toFixed(1) : '0.0'
    const bytes = this.#stats.frameBytes ? (this.#stats.frameBytes / 1024).toFixed(1) : '0.0'
    const cap = this.#stream.networkEnabled ? `${(this.#stream.bandwidthBytesPerSecond / 1024).toFixed(0)} KB/s` : '不限速'
    this.metrics.textContent = `${width}×${height} · 画质 ${this.#stream.quality} · 设定 ${fps} fps · 收 ${measured} fps · 绘 ${this.#stats.renderFps.toFixed(1)} fps · 平均 ${bytes} KB/帧 · 当前 ${this.#stats.lastFrameBytes} B · 丢 ${this.#stats.droppedFrames} · 限 ${cap} · 延迟 ${this.#stream.latencyMillis} ms`
  }

  #renderChunk(buffer) {
    const view = new DataView(buffer)
    if (view.byteLength < 12 || view.getUint32(0) !== 0x42535452) return
    const frameId = view.getUint32(4)
    const chunkIndex = view.getUint16(8)
    const chunkCount = view.getUint16(10)
    if (chunkCount === 0 || chunkIndex >= chunkCount) return
    for (const pendingFrameId of this.#chunks.keys()) {
      if (pendingFrameId + 2 < frameId) this.#chunks.delete(pendingFrameId)
    }
    let frame = this.#chunks.get(frameId)
    if (!frame || frame.count !== chunkCount) {
      frame = {count: chunkCount, chunks: new Array(chunkCount), received: 0, bytes: 0}
      this.#chunks.set(frameId, frame)
    }
    if (!frame.chunks[chunkIndex]) {
      const chunk = new Uint8Array(buffer.slice(12))
      frame.chunks[chunkIndex] = chunk
      frame.received += 1
      frame.bytes += chunk.byteLength
    }
    if (frame.received !== frame.count) return
    const merged = new Uint8Array(frame.bytes)
    let offset = 0
    for (const chunk of frame.chunks) {
      merged.set(chunk, offset)
      offset += chunk.byteLength
    }
    this.#chunks.delete(frameId)
    this.#renderFrame(merged.buffer, frameId)
  }

  #renderFrame(buffer, frameId) {
    if (frameId < this.#latestFrameId) return
    this.#latestFrameId = frameId
    this.#stats.frames += 1
    this.#stats.bytes += buffer.byteLength
    this.#stats.lastFrameBytes = buffer.byteLength
    if (this.#pending) this.#stats.droppedFrames += 1
    const now = performance.now()
    const elapsed = now - this.#stats.windowStart
    if (elapsed >= 1000) {
      this.#stats.fps = this.#stats.frames * 1000 / elapsed
      this.#stats.renderFps = this.#stats.renderFrames * 1000 / elapsed
      this.#stats.frameBytes = this.#stats.bytes / this.#stats.frames
      this.#stats.frames = 0
      this.#stats.bytes = 0
      this.#stats.renderFrames = 0
      this.#stats.windowStart = now
      this.#renderMetrics()
    }
    this.#pending = {frameId, buffer}
    this.#scheduleDraw()
  }

  #scheduleDraw() {
    if (this.#drawing || !this.#pending) return
    this.#drawing = true
    requestAnimationFrame(async () => {
      const pending = this.#pending
      this.#pending = null
      try {
        const bitmap = await createImageBitmap(new Blob([pending.buffer], {type: 'image/jpeg'})).catch(() => null)
        if (!bitmap) return
        try {
          this.#canvas.getContext('2d').drawImage(bitmap, 0, 0, this.#canvas.width, this.#canvas.height)
          this.#stats.renderFrames += 1
        } finally {
          bitmap.close()
        }
      } finally {
        this.#drawing = false
        if (this.#pending) this.#scheduleDraw()
        this.#renderHud()
      }
    })
  }

  #navigate() {
    const url = this.address.value.trim()
    if (!url || !this.#activeTabId) return
    let target = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url) ? url : `https://${url}`
    if (/^https?:\/\//i.test(target)) {
      try {
        target = new URL(target).href
      } catch {
      }
    }
    this.address.value = target
    this.navigationStatus.textContent = `正在打开：${target}`
    this.navigationStatus.style.color = '#666'
    this.#send({type: 'tab.navigate', url: target})
  }

  #point(event, box = this.#canvas.getBoundingClientRect()) {
    return {
      x: (event.clientX - box.left) * this.#canvas.width / box.width,
      y: (event.clientY - box.top) * this.#canvas.height / box.height,
    }
  }

  #pointerDown(event) {
    this.#canvas.focus({preventScroll: true})
    const point = this.#point(event)
    if (event.button === 2) {
      this.#send(this.#pointerMessage('context', event, point))
      return
    }
    this.#send(this.#pointerMessage('down', event, point))
    try {
      this.#canvas.setPointerCapture(event.pointerId)
    } catch {
    }
  }

  #pointerMove(event) {
    const coalesced = event.getCoalescedEvents ? event.getCoalescedEvents() : []
    this.#pendingMove = coalesced.length ? coalesced[coalesced.length - 1] : event
    if (this.#moveFrame) return
    this.#moveFrame = requestAnimationFrame(() => {
      this.#moveFrame = 0
      const sample = this.#pendingMove
      this.#pendingMove = null
      if (sample) this.#send(this.#pointerMessage('move', sample, this.#point(sample)))
    })
  }

  #pointerUp(event) {
    if (event.button === 2) return
    this.#send(this.#pointerMessage('up', event, this.#point(event)))
    if (this.#canvas.hasPointerCapture(event.pointerId)) this.#canvas.releasePointerCapture(event.pointerId)
  }

  #pointerCancel(event) {
    this.#send(this.#pointerMessage('cancel', event, this.#point(event)))
    if (this.#canvas.hasPointerCapture(event.pointerId)) this.#canvas.releasePointerCapture(event.pointerId)
  }

  #pointerMessage(type, event, point) {
    const moving = type === 'move'
    return {
      type,
      ...point,
      button: moving ? -1 : event.button,
      buttons: event.buttons,
      clickCount: moving ? 1 : Math.max(1, Math.min(3, event.detail || 1)),
      seq: this.#pointerSequence++,
      ...this.#modifiers(event),
    }
  }

  #modifiers(event) {
    return {
      alt: event.altKey,
      ctrl: event.ctrlKey,
      meta: event.metaKey,
      shift: event.shiftKey,
    }
  }

  #wheel(event) {
    event.preventDefault()
    const scale = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? this.#canvas.height : 1
    this.#send({
      type: 'wheel',
      ...this.#point(event),
      deltaX: event.deltaX * scale,
      deltaY: event.deltaY * scale,
    })
  }

  #key(event) {
    event.preventDefault()
    if (event.isComposing) return
    this.#send({
      type: 'key',
      key: event.key,
      code: event.code,
      keyCode: event.keyCode,
      location: event.location,
      repeat: event.repeat,
      ...this.#modifiers(event),
    })
    if ((event.ctrlKey || event.metaKey) && ['c', 'x'].includes(event.key.toLowerCase())) {
      setTimeout(() => this.#send({type: 'clipboard.read'}), 150)
    }
  }

  #applyCursor(value) {
    if (!this.#canvas) return
    const raw = typeof value === 'string' ? value.trim() : ''
    const fallback = raw.includes('url(') ? (raw.split(',').pop() || 'default').trim() : raw
    this.#canvas.style.cursor = /^[a-z-]+$/.test(fallback) ? fallback : 'default'
  }

  async #copyToLocal(text) {
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch (ignored) {
    }
    const area = document.createElement('textarea')
    area.value = text
    area.style.cssText = 'position:fixed;opacity:0;pointer-events:none'
    document.body.append(area)
    area.select()
    try {
      document.execCommand('copy')
    } finally {
      area.remove()
    }
  }

  #send(value) {
    if (this.#socket?.readyState === WebSocket.OPEN) this.#socket.send(JSON.stringify(value))
  }
}

customElements.define('browser-stream', BrowserStreamElement)
