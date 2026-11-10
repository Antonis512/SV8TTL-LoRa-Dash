"""Run production migration SQL against SQLite, preserving an existing map/history.

The tiny Java host executes ChatStore's actual migration method, including its
idempotency checks. No Android device or APK is required.
"""
from pathlib import Path
import os
import re
import sqlite3
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/ChatStore.java").read_text()

def method(text, signature):
    start = text.index(signature)
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]

migration = method(source, "private void addMapPositionMetadata")
host = r'''
import java.util.*;
public class MapMigrationProbe {
 static class Cursor implements AutoCloseable {
  Iterator<String> values;String value;
  Cursor(Set<String> values){this.values=new ArrayList<>(values).iterator();}
  boolean moveToNext(){if(!values.hasNext())return false;value=values.next();return true;}
  String getString(int index){return value;}
  public void close(){}
 }
 static class SQLiteDatabase {
  Map<String,Set<String>> columns=new HashMap<>();List<String> sql=new ArrayList<>();
  Cursor rawQuery(String query,Object args){String table=query.substring(query.indexOf('(')+1,query.indexOf(')'));return new Cursor(columns.computeIfAbsent(table,k->new HashSet<>()));}
  void execSQL(String query){String[] parts=query.split(" ");columns.get(parts[2]).add(parts[5]);sql.add(query);}
 }
 __MIGRATION__
 public static void main(String[] args){
  MapMigrationProbe probe=new MapMigrationProbe();SQLiteDatabase db=new SQLiteDatabase();
  probe.addMapPositionMetadata(db);int count=db.sql.size();probe.addMapPositionMetadata(db);
  if(count!=10||db.sql.size()!=count)throw new AssertionError("Migration must add all columns once");
  for(String sql:db.sql)System.out.println(sql);
 }
}
'''.replace("__MIGRATION__", migration)

jdk = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
with tempfile.TemporaryDirectory(prefix="map-migration-") as tmp:
    java = Path(tmp) / "MapMigrationProbe.java"
    java.write_text(host)
    subprocess.run([str(jdk / "javac") if jdk else "javac", "-d", tmp, str(java)], check=True)
    sql = subprocess.check_output([str(jdk / "java") if jdk else "java", "-cp", tmp, "MapMigrationProbe"], text=True).splitlines()

db = sqlite3.connect(":memory:")
create = method(source, "private void createMapTables")
for statement in re.findall(r'db\.execSQL\("([^"\n]+)"\)', create):
    db.execute(statement)
db.execute("INSERT INTO map_nodes(radio,device,id,name,lat,lon,position_at) VALUES('mt','rx','!291b406e','Klif Aegina',38,23,1700000000000)")
db.execute("INSERT INTO map_positions(radio,device,node,lat,lon,reported_at) VALUES('mt','rx','!291b406e',38,23,1700000000000)")
for statement in sql:
    db.execute(statement)
assert db.execute("SELECT name,lat,lon,position_at,position_time,gps_time,precision_bits,position_source FROM map_nodes").fetchone() == (
    "Klif Aegina", 38, 23, 1700000000000, 0, 0, 0, "legacy")
assert db.execute("SELECT COUNT(*) FROM map_positions").fetchone()[0] == 1
db.execute("UPDATE map_nodes SET position_time=1700000010000,gps_time=1700000000000,position_received=1700000020000,precision_bits=13,position_source='mt_snapshot'")
db.execute("CREATE TABLE node_heard(radio TEXT,node_id TEXT,peer TEXT,settings TEXT)")
map_source = (root / "src/main/java/MapData.java").read_text()
snapshot_query = re.search(r'rawQuery\("(SELECT n\.radio[^"\n]+)"', map_source).group(1)
row = db.execute(snapshot_query).fetchone()
assert row[10:] == (1700000010000, 1700000000000, 1700000020000, 13, "mt_snapshot"), row
assert 'null, 19)' in source and 'if(oldVersion<19)addMapPositionMetadata(db)' in source
print("Map metadata migration passed: legacy coordinates/history preserved, no invented timestamps, idempotent columns, and actual snapshot SQL retains precision/provenance")
