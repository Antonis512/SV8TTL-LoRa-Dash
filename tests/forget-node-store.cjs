const assert=require('node:assert/strict');
const fs=require('node:fs');
const {DatabaseSync}=require('node:sqlite');
const source=fs.readFileSync('src/main/java/ChatStore.java','utf8');
const method=source.split('void forgetNode(')[1].split('private void createSignalHistory')[0];
const db=new DatabaseSync(':memory:');
const tables=['messages','node_heard','map_nodes','map_positions','map_heard','map_geofence_state','signal_history'];
for(const table of tables){
  db.exec(`CREATE TABLE ${table}(radio TEXT,peer TEXT,sender TEXT,own INTEGER,body TEXT,id TEXT,node TEXT)`);
  const insert=db.prepare(`INSERT INTO ${table} VALUES(?,?,?,?,?,?,?)`);
  insert.run('mc','target','target',0,'A%_: hello','target','target');
  insert.run('mc','other','other',0,'Another: hello','other','other');
  insert.run('mt','target','target',0,'A%_: hello','target','target');
}
db.prepare('INSERT INTO messages VALUES(?,?,?,?,?,?,?)').run('mc','','MeshCore',0,'A%_: channel message','','');
db.prepare('INSERT INTO messages VALUES(?,?,?,?,?,?,?)').run('mc','','MeshCore',0,'AXX: keep this message','','');
// Execute the actual production WHERE clauses, including its parameter order.
const radio='mc',peer='target',senderName='A%_';
for(const match of method.matchAll(/db\.delete\(("[^"]+"|table),"([^"]+)",\s*new String\[\]\{([^}]+)\}\)/g)){
  const names=match[1]==='table'?['map_positions','map_heard','map_geofence_state']:[JSON.parse(match[1])];
  const params=match[3].split(',').map(s=>({radio,peer,senderName})[s.trim()]);
  for(const table of names){
    if(table==='signal_history'&&radio!=='mt')continue;
    db.prepare(`DELETE FROM ${table} WHERE ${match[2]}`).run(...params);
  }
}
for(const table of tables.filter(t=>t!=='signal_history')){
  assert.equal(db.prepare(`SELECT count(*) AS n FROM ${table} WHERE radio='mc' AND (peer='target' OR id='target' OR node='target')`).get().n,0,table);
  assert.equal(db.prepare(`SELECT count(*) AS n FROM ${table} WHERE radio='mt'`).get().n,1,table+' preserves other network');
}
assert.equal(db.prepare("SELECT count(*) AS n FROM messages WHERE sender='MeshCore'").get().n,1);
assert.equal(db.prepare("SELECT body FROM messages WHERE sender='MeshCore'").get().body,'AXX: keep this message');
assert.equal(db.prepare('SELECT count(*) AS n FROM signal_history').get().n,3);
console.log('Forget Node storage deletion, network isolation and literal sender-name matching passed');
db.close();
