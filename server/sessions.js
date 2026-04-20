const sessions = new Map();

function getOrCreate(familyId) {
  if (!sessions.has(familyId)) {
    sessions.set(familyId, {
      child: null,
      parents: new Set(),
      listeningParents: new Set(),
      dailyStreamedSeconds: 0,
      dailyResetAt: Date.now(),
    });
  }
  return sessions.get(familyId);
}

function removeClient(ws) {
  for (const [familyId, session] of sessions) {
    if (session.child === ws) {
      session.child = null;
      session.listeningParents.clear();
      return { familyId, role: 'child' };
    }
    if (session.parents.has(ws)) {
      session.parents.delete(ws);
      const wasListening = session.listeningParents.delete(ws);
      return { familyId, role: 'parent', wasListening };
    }
  }
  return null;
}

module.exports = { getOrCreate, removeClient, sessions };
