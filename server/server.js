const WebSocket = require('ws');

const wss = new WebSocket.Server({ port: 613 });

const session = {
  child: null,
  parents: new Set(),
  listeningParents: new Set()
};

function safeSend(ws, data) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(data);
}

function broadcastParents(data) {
  session.parents.forEach(p => safeSend(p, data));
}

function notifyStatus() {
  const msg = JSON.stringify({ type: 'status', listeningCount: session.listeningParents.size });
  broadcastParents(msg);
}

wss.on('connection', (ws) => {
  console.log('Client connected');

  ws.on('message', (data, isBinary) => {
    if (isBinary) {
      if (ws !== session.child) return; // only child may send audio
      session.listeningParents.forEach(p => safeSend(p, data));
      return;
    }

    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch (_) {
      return; // ignore malformed frames
    }

    if (msg.type === 'register') {
      if (msg.role === 'child') {
        if (session.child && session.child !== ws) {
          console.warn('Second child registered — closing previous child connection');
          session.child.close(1000, 'replaced');
        }
        session.child = ws;
        console.log('Child registered');
      } else if (msg.role === 'parent') {
        session.parents.add(ws);
        console.log(`Parent registered (total: ${session.parents.size})`);
        safeSend(ws, JSON.stringify({ type: 'status', listeningCount: session.listeningParents.size }));
      }
    } else if (msg.type === 'start_listen') {
      if (!session.parents.has(ws)) return;
      const wasZero = session.listeningParents.size === 0;
      session.listeningParents.add(ws);
      if (wasZero) safeSend(session.child, JSON.stringify({ type: 'start_stream' }));
      notifyStatus();
    } else if (msg.type === 'stop_listen') {
      if (!session.parents.has(ws)) return;
      session.listeningParents.delete(ws);
      if (session.listeningParents.size === 0) {
        safeSend(session.child, JSON.stringify({ type: 'stop_stream' }));
      }
      notifyStatus();
    }
  });

  ws.on('close', () => {
    if (ws === session.child) {
      session.child = null;
      console.log('Child disconnected');
    } else {
      session.parents.delete(ws);
      const wasListening = session.listeningParents.delete(ws);
      if (wasListening && session.listeningParents.size === 0) {
        safeSend(session.child, JSON.stringify({ type: 'stop_stream' }));
      }
      notifyStatus();
    }
  });

  ws.on('error', err => console.error('Socket error:', err.message));
});

console.log('Relay server listening on port 613');
