#!/usr/bin/env node
/**
 * Pre-merge, read-only guard for the original Flyway history.
 * The historical V9 deletion migration MUST stay byte-identical and must
 * already be recorded in flyway_schema_history on any real restored DB.
 *
 * This checks repository files only, NEVER connects to a DB or executes SQL.
 */
import { readdirSync, readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const migrationDir = fileURLToPath(new URL('../src/main/resources/db/migration/', import.meta.url));
const paths = readdirSync(migrationDir).filter(x => /^V\d+__.+\.sql$/.test(x));
const ordered = paths.map(name => ({name, version:Number(/^V(\d+)__/.exec(name)[1])}))
  .sort((a,b)=>a.version-b.version);
if(!ordered.length || ordered[0].version!==1) throw Error('MIGRATIONS_MISSING');
for(let i=1;i<ordered.length;i++){
  if(ordered[i].version!==ordered[i-1].version+1) throw Error('FLYWAY_SEQUENCE_GAP_OR_DUPLICATE');
}
const reset = 'V9__reset_operational_data.sql';
// Pinned existing Git blob SHA (not the post-edit working tree): do not change
// or permit cherry-picked deletion SQL under a new version.
const expectedBlob='a2b1fa1d325aad03d137573e128435dd4bda98ad';
function gitBlobSha(buf){
  const h=createHash('sha1');
  h.update(Buffer.from('blob '+buf.length+'\0'));
  h.update(buf);
  return h.digest('hex');
}
let riskyCount=0;
for(const item of ordered){
 const bytes=readFileSync(join(migrationDir,item.name));
 const sql=bytes.toString('utf8').replace(/--[^\n]*/g,' ').replace(/\/\*[\s\S]*?\*\//g,' ');
 const destructive=/\b(?:DELETE\s+FROM|TRUNCATE(?:\s+TABLE)?|DROP\s+(?:TABLE|DATABASE)|UPDATE\s+\w+\s+SET\s+.*?WHERE\s+1\s*=\s*1)\b/i;
 if(item.name===reset){
   if(gitBlobSha(bytes)!==expectedBlob) throw Error('DESTRUCTIVE_V9_CONTENT_CHANGED');
   if(!/\bDELETE\s+FROM\b/i.test(sql)) throw Error('V9_RESET_NO_LONGER_IDENTIFIABLE');
   riskyCount++;
 }else if(destructive.test(sql)){
   throw Error('UNREVIEWED_DESTRUCTIVE_MIGRATION_V'+item.version);
 }
}
if(riskyCount!==1)throw Error('HISTORICAL_V9_RESET_MISSING');
console.log('PULSE MIGRATION SAFETY PASS: Flyway V1–V'+ordered.at(-1).version+
  ', historical destructive V9 is byte-pinned; no other destructive migration detected.');
