const { getOrCreate, removeClient, sessions } = require('../sessions');

beforeEach(() => sessions.clear());

test('getOrCreate creates session for new familyId', () => {
  const s = getOrCreate('fam1');
  expect(s.child).toBeNull();
  expect(s.parents.size).toBe(0);
  expect(s.listeningParents.size).toBe(0);
});

test('getOrCreate returns same session for same familyId', () => {
  const s1 = getOrCreate('fam1');
  const s2 = getOrCreate('fam1');
  expect(s1).toBe(s2);
});

test('removeClient finds child by ws reference', () => {
  const ws = { id: 'child-ws' };
  const s = getOrCreate('fam1');
  s.child = ws;
  const result = removeClient(ws);
  expect(result).toEqual({ familyId: 'fam1', role: 'child' });
  expect(s.child).toBeNull();
});

test('removeClient finds parent and reports wasListening', () => {
  const ws = { id: 'parent-ws' };
  const s = getOrCreate('fam1');
  s.parents.add(ws);
  s.listeningParents.add(ws);
  const result = removeClient(ws);
  expect(result.role).toBe('parent');
  expect(result.wasListening).toBe(true);
  expect(s.parents.has(ws)).toBe(false);
});

test('removeClient returns null for unknown ws', () => {
  expect(removeClient({ id: 'unknown' })).toBeNull();
});
