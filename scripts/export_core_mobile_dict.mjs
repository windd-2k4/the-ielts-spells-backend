import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const backendRoot = path.resolve(import.meta.dirname, '..');
const sqlitePath = path.join(backendRoot, 'data', 'dictionary', 'ielts_dict.sqlite');
const outPath = path.resolve(backendRoot, '..', 'the-ielts-spells-mobile', 'assets', 'dict', 'core_ielts_dict.json');

const db = new DatabaseSync(sqlitePath, { readOnly: true });

function cleanSummary(rawSummary, payload) {
  let s = (rawSummary || '').replace(/\s+/g, ' ').trim();
  const firstMeaning = payload?.meanings?.[0]?.items?.[0]?.meaning?.replace(/^[-•\s]+/, '').trim() || '';
  if (!s || s.endsWith(':')) {
    const pos = s.replace(/:$/, '').trim();
    if (firstMeaning) {
      return pos ? `${pos}: ${firstMeaning}` : firstMeaning;
    }
  }
  return s || firstMeaning || '';
}

function compactMeanings(meanings, maxGroups = 2, maxItems = 2, includeExamples = false) {
  if (!Array.isArray(meanings)) return [];
  return meanings.slice(0, maxGroups).map((m) => ({
    partOfSpeech: m.partOfSpeech,
    items: Array.isArray(m.items)
      ? m.items.slice(0, maxItems).map((it) => ({
          meaning: it.meaning,
          examples: includeExamples && Array.isArray(it.examples) ? it.examples.slice(0, 1) : [],
        }))
      : [],
  }));
}

const coreEntries = [];
const addedEn = new Set();
const addedVi = new Set();
const linkedEnglishWords = new Set();

for (const row of db.prepare(`
  SELECT direction, headword, headword_lower, ipa, cefr_level, part_of_speech, summary, payload_json, source
  FROM dictionary_entries
  WHERE source IN ('AVDICT+TDICT', 'TDICT')
`).iterate()) {
  let payload = {};
  try { payload = JSON.parse(row.payload_json || '{}'); } catch {}

  if (Array.isArray(payload.wordFamily)) {
    for (const wf of payload.wordFamily) {
      if (wf?.word) linkedEnglishWords.add(String(wf.word).trim().toLowerCase());
    }
  }
  if (Array.isArray(payload.ieltsDefinitions)) {
    for (const def of payload.ieltsDefinitions) {
      if (Array.isArray(def?.synonyms)) {
        for (const syn of def.synonyms) {
          if (syn) linkedEnglishWords.add(String(syn).trim().toLowerCase());
        }
      }
    }
  }

  addedEn.add(row.headword_lower);
  coreEntries.push({
    d: 'EN_VI',
    w: row.headword,
    i: row.ipa || undefined,
    c: row.cefr_level || undefined,
    p: row.part_of_speech || undefined,
    s: cleanSummary(row.summary, payload),
    pl: {
      phoneticUk: payload.phoneticUk || undefined,
      phoneticUs: payload.phoneticUs || undefined,
      wordFamily: Array.isArray(payload.wordFamily) ? payload.wordFamily.slice(0, 4) : undefined,
      ieltsDefinitions: Array.isArray(payload.ieltsDefinitions) ? payload.ieltsDefinitions.slice(0, 1) : undefined,
      meanings: compactMeanings(payload.meanings, 2, 2, true),
    },
    src: row.source,
  });
}

const lookupEnStmt = db.prepare(`
  SELECT headword, headword_lower, ipa, part_of_speech, summary, payload_json, source
  FROM dictionary_entries
  WHERE direction = 'EN_VI' AND headword_lower = ?
  LIMIT 1
`);

let linkedAdded = 0;
for (const word of linkedEnglishWords) {
  if (!word || addedEn.has(word)) continue;
  const row = lookupEnStmt.get(word);
  if (!row) continue;
  let payload = {};
  try { payload = JSON.parse(row.payload_json || '{}'); } catch {}
  addedEn.add(row.headword_lower);
  linkedAdded++;
  coreEntries.push({
    d: 'EN_VI',
    w: row.headword,
    i: row.ipa || undefined,
    p: row.part_of_speech || undefined,
    s: cleanSummary(row.summary, payload),
    pl: { meanings: compactMeanings(payload.meanings, 2, 2, false) },
    src: row.source,
  });
}

let extraEnAdded = 0;
for (const row of db.prepare(`
  SELECT headword, headword_lower, ipa, part_of_speech, summary, payload_json, source
  FROM dictionary_entries
  WHERE direction = 'EN_VI'
    AND headword NOT LIKE '% %'
    AND headword NOT LIKE '%-%'
    AND LENGTH(headword) BETWEEN 2 AND 12
  ORDER BY LENGTH(headword), id
  LIMIT 5000
`).iterate()) {
  if (addedEn.has(row.headword_lower)) continue;
  let payload = {};
  try { payload = JSON.parse(row.payload_json || '{}'); } catch {}
  addedEn.add(row.headword_lower);
  extraEnAdded++;
  coreEntries.push({
    d: 'EN_VI',
    w: row.headword,
    i: row.ipa || undefined,
    p: row.part_of_speech || undefined,
    s: cleanSummary(row.summary, payload),
    pl: { meanings: compactMeanings(payload.meanings, 1, 2, false) },
    src: row.source,
  });
}

let viAdded = 0;
for (const row of db.prepare(`
  SELECT headword, headword_lower, part_of_speech, summary, payload_json, source
  FROM dictionary_entries
  WHERE direction = 'VI_EN' AND LENGTH(headword) <= 18
  ORDER BY LENGTH(headword), id
  LIMIT 12000
`).iterate()) {
  if (addedVi.has(row.headword_lower)) continue;
  let payload = {};
  try { payload = JSON.parse(row.payload_json || '{}'); } catch {}
  addedVi.add(row.headword_lower);
  viAdded++;
  coreEntries.push({
    d: 'VI_EN',
    w: row.headword,
    p: row.part_of_speech || undefined,
    s: cleanSummary(row.summary, payload),
    pl: { meanings: compactMeanings(payload.meanings, 1, 2, false) },
    src: row.source,
  });
}

db.close();
fs.writeFileSync(outPath, JSON.stringify(coreEntries));
const mb = (fs.statSync(outPath).size / 1048576).toFixed(2);
console.log(`Exported ${coreEntries.length} compact core entries (linked: ${linkedAdded}, extraEn: ${extraEnAdded}, vi: ${viAdded}, size: ${mb} MB)`);
